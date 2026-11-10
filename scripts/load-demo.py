"""Load all synthetic scenario reports via HTTP, retaining expected row errors."""
import argparse,json,pathlib,uuid
from decimal import Decimal
from benchmark import Client,environment,ROOT
from generate_reports import generate

def main():
    parser=argparse.ArgumentParser();parser.add_argument("--base",default="http://127.0.0.1:5173");parser.add_argument("--xlsx",action="store_true");args=parser.parse_args()
    prefix="DEMO-"+uuid.uuid4().hex[:8];folder=ROOT/".local"/prefix
    generate(folder,40,prefix,xlsx=args.xlsx)
    env=environment();client=Client(args.base);client.login("finance",env["DEMO_FINANCE_PASSWORD"])
    reports=[]
    for name,kind in [("orders","ORDERS"),("settlements","SETTLEMENTS"),("cod","COD"),("freight","FREIGHT"),("returns","RETURNS")]:
        path=folder/(name+(".xlsx" if args.xlsx else ".csv"))
        b=client.upload(path,"Website",kind);id=b["id"];d=client.wait(id,"AWAITING_MAPPING")
        mapping={field:field for field in d["fields"] if field in d["headers"]}
        client.request("/imports/"+id+"/preview",{"mapping":mapping})
        client.request("/imports/"+id+"/approve",{"mapping":mapping,"approve":True})
        result=client.wait(id,"COMPLETE");reports.append({"type":kind,"accepted":result["accepted"],"errors":result["error_count"]})
    orders=client.request("/orders?"+__import__("urllib.parse",fromlist=["urlencode"]).urlencode({"q":prefix,"size":100}))["rows"]
    risk=sum((Decimal(str(row["money_at_risk"])) for row in orders),Decimal("0.00"))
    statuses=sorted({row["status"] for row in orders})
    assert len(orders)==40,(len(orders),reports)
    assert risk==Decimal("5992.00"),(risk,statuses)
    assert reports[0]["errors"]==3 and reports[1]["errors"]==2,reports
    assert {"SETTLED","SHORT_PAID","UNPAID_PAST_DUE","PENDING","FREIGHT_OVERCHARGED","RETURN_NOT_RECEIVED"}<=set(statuses),statuses
    result={"prefix":prefix,"format":"XLSX" if args.xlsx else "CSV","orders":40,"money_at_risk":str(risk),"statuses":statuses,"reports":reports}
    print(json.dumps(result,indent=2))
    (ROOT/".local"/"demo-load-result.json").write_text(json.dumps(result,indent=2),encoding="utf-8")

if __name__=="__main__":main()
