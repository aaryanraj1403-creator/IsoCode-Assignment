"""Real HTTP upload -> parse -> preview -> approve -> PostgreSQL -> reconciliation benchmark.
Human review latency and dataset generation are excluded; network polling is included.
Uses existing .env credentials. Creates synthetic records; does not delete any data.
"""
import argparse,csv,http.cookiejar,json,os,pathlib,platform,time,urllib.request,uuid
from generate_reports import generate

ROOT=pathlib.Path(__file__).resolve().parents[1]
def environment():
    values={}
    file=ROOT/".env"
    if file.exists():
        for line in file.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.startswith("#"):
                key,value=line.split("=",1);values[key]=value
    return values|dict(os.environ)

class Client:
    def __init__(self,base):
        self.base=base.rstrip("/");self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def request(self,path,data=None,content_type="application/json"):
        headers={}
        if data is not None:
            token=self.request("/auth/csrf");headers[token["headerName"]]=token["token"];headers["Content-Type"]=content_type
            if isinstance(data,dict):data=json.dumps(data).encode()
        req=urllib.request.Request(self.base+"/api"+path,data=data,headers=headers)
        try:
            with self.opener.open(req,timeout=120) as response:
                raw=response.read();return json.loads(raw) if raw else None
        except urllib.error.HTTPError as exc:raise RuntimeError(f"{path}: HTTP {exc.code}: {exc.read().decode()}") from exc
    def login(self,username,password):
        self.request("/auth/login",urllib.parse.urlencode({"username":username,"password":password}).encode(),"application/x-www-form-urlencoded")
    def upload(self,path,partner,report):
        boundary="reconflow"+uuid.uuid4().hex
        parts=[]
        for key,value in {"partner":partner,"type":report}.items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode())
        parts.extend([path.read_bytes(),f"\r\n--{boundary}--\r\n".encode()])
        return self.request("/imports",b"".join(parts),"multipart/form-data; boundary="+boundary)
    def wait(self,id,state):
        start=time.perf_counter()
        while time.perf_counter()-start<300:
            batch=self.request("/imports/"+id)
            if batch["state"]==state:return batch
            if batch["state"]=="FAILED":raise RuntimeError(batch["error_message"])
            time.sleep(.1)
        raise TimeoutError(id)
    def process(self,path,partner,report):
        start=time.perf_counter();batch=self.upload(path,partner,report);id=batch["id"];parsed=self.wait(id,"AWAITING_MAPPING")
        with path.open(encoding="utf-8") as file:fields=next(csv.reader(file))
        mapping={f:f for f in fields}
        preview=self.request("/imports/"+id+"/preview",{"mapping":mapping})
        if any("error" in row for row in preview):raise RuntimeError(preview)
        self.request("/imports/"+id+"/approve",{"mapping":mapping,"approve":True})
        completed=self.wait(id,"COMPLETE")
        result={"type":report,"import_id":id,"rows":completed["row_count"],"accepted":completed["accepted"],"errors":completed["error_count"],"seconds":round(time.perf_counter()-start,3)}
        if result["accepted"]!=result["rows"] or result["errors"]:raise RuntimeError(result)
        print(json.dumps(result),flush=True);return result

def main():
    p=argparse.ArgumentParser();p.add_argument("--rows",type=int,default=100000);p.add_argument("--base",default="http://127.0.0.1:5173");p.add_argument("--out",default=".local/benchmark-result.json");args=p.parse_args()
    env=environment();prefix="BENCH-"+uuid.uuid4().hex[:10];folder=ROOT/".local"/prefix
    generate(folder,args.rows,prefix,True)
    client=Client(args.base);client.login("finance",env["DEMO_FINANCE_PASSWORD"])
    baseline=client.request("/orders?size=1")["total"]
    start=time.perf_counter()
    reports=[client.process(folder/"orders.csv","Website","ORDERS"),client.process(folder/"settlements.csv","Website","SETTLEMENTS")]
    total=time.perf_counter()-start
    orders=client.request("/orders?"+urllib.parse.urlencode({"q":prefix,"size":1}))
    if orders["total"]!=args.rows:raise AssertionError("Missing reconciled orders")
    settled=client.request("/orders?"+urllib.parse.urlencode({"q":prefix,"status":"SETTLED","size":1}))
    if settled["total"]!=args.rows:raise AssertionError("Not all benchmark orders settled")
    runtime=client.request("/system-info")
    result={"environment":{"os":platform.platform(),"python":platform.python_version(),"cpu":platform.processor(),"logical_processors":os.cpu_count(),"runtime":runtime},
        "records_before":baseline,"orders":args.rows,"total_report_rows":args.rows*2,"end_to_end_seconds":round(total,3),"under_one_minute":total<60,
        "includes":["HTTP multipart upload","SHA-256","streaming parsing","raw row storage","20-row preview","mapping approval","row validation","database normalisation","reconciliation after both files","HTTP polling"],
        "excludes":["dataset generation","human think time","dependency installation","cold JVM startup"],"reports":reports}
    out=ROOT/args.out;out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2),encoding="utf-8")
    print(json.dumps(result,indent=2),flush=True)

if __name__=="__main__":main()
