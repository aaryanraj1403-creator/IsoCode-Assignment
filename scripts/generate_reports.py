"""Deterministic synthetic reports; standard-library only. Never uses personal data."""
import argparse, csv, datetime as dt, pathlib, zipfile
from xml.sax.saxutils import escape

def write_csv(path, headers, rows):
    with path.open("w", newline="", encoding="utf-8") as out:
        writer=csv.writer(out); writer.writerow(headers); writer.writerows(rows)

def xlsx_from_csv(source):
    """Minimal values-only, single-sheet XLSX with inline strings (no formulas/macros)."""
    target=source.with_suffix(".xlsx")
    with zipfile.ZipFile(target,"w",zipfile.ZIP_DEFLATED) as book:
        book.writestr("[Content_Types].xml",'<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>')
        book.writestr("_rels/.rels",'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>')
        book.writestr("xl/workbook.xml",'<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Report" sheetId="1" r:id="rId1"/></sheets></workbook>')
        book.writestr("xl/_rels/workbook.xml.rels",'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>')
        with book.open("xl/worksheets/sheet1.xml","w") as sheet,source.open(encoding="utf-8",newline="") as src:
            sheet.write(b'<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>')
            for index,row in enumerate(csv.reader(src),1):
                cells=[]
                for col,value in enumerate(row):
                    n=col+1; letters=""
                    while n: n,rem=divmod(n-1,26);letters=chr(65+rem)+letters
                    cells.append(f'<c r="{letters}{index}" t="inlineStr"><is><t>{escape(value)}</t></is></c>')
                sheet.write((f'<row r="{index}">'+''.join(cells)+'</row>').encode())
            sheet.write(b'</sheetData></worksheet>')
    return target

def generate(out,rows=100,prefix="SYN",benchmark=False,xlsx=False):
    out=pathlib.Path(out);out.mkdir(parents=True,exist_ok=True)
    today=dt.date.today(); ordered=today-dt.timedelta(days=35); due=ordered+dt.timedelta(days=7)
    orders=[];settlements=[];freight=[];returns=[];cod=[]
    for i in range(rows):
        oid=f"{prefix}-{i:06d}"; case=0 if benchmark else i%10
        delivery="RETURNED" if case==4 else "RETURN_EXPECTED" if case==6 else "DELIVERED"
        payment="COD" if case==2 else "PREPAID"
        orders.append([oid,"1000.00",payment,delivery,ordered.isoformat(),(today+dt.timedelta(days=7) if case==8 else due).isoformat()])
        expected="-96.00" if case in (4,6) else "904.00" if case==7 else "910.00"
        if case==2: continue
        amount="-142.00" if case==4 else "873.00" if case==1 else "900.00" if case==9 else "0.00" if case==8 else expected
        if case==3:
            settlements.extend([[f"{prefix}-T{i}a",oid,"455.00",due.isoformat(),"0","0"],[f"{prefix}-T{i}b",oid,"455.00",due.isoformat(),"0","0"]])
        else:settlements.append([f"{prefix}-T{i}",oid,amount,due.isoformat(),"5" if case==7 else "0","1" if case==7 else "0"])
        if case==5:freight.append([f"{prefix}-F{i}",oid,"100.00",due.isoformat()])
        if case==6 and i%20==16:returns.append([f"{prefix}-R{i}",oid,due.isoformat()])
    if not benchmark:
        orders.extend([[f"{prefix}-INVALID-AMOUNT","not-money","COD","DELIVERED",ordered.isoformat(),due.isoformat()],[f"{prefix}-INVALID-DATE","1000","COD","DELIVERED","2026-02-30",due.isoformat()],orders[0]])
        settlements.extend([settlements[0],[f"{prefix}-MISSING-ID","","100",due.isoformat(),"0","0"],[f"{prefix}-INVALID-TAX",f"{prefix}-000000","100",due.isoformat(),"-5","0"]])
        cod.append([f"{prefix}-COD-LATE",f"{prefix}-000012","100.00",due.isoformat(),"0","0"])
    files={
        "orders":(["order_id","value","payment","delivery","order_date","due_date"],orders),
        "settlements":(["transaction_id","order_id","amount","date","tcs","tds"],settlements),
        "freight":(["transaction_id","order_id","amount","date"],freight),
        "returns":(["transaction_id","order_id","date"],returns),
        "cod":(["transaction_id","order_id","amount","date","tcs","tds"],cod),
    }
    for name,(headers,data) in files.items():
        path=out/f"{name}.csv";write_csv(path,headers,data)
        if xlsx:xlsx_from_csv(path)
    print(f"Generated {rows:,} synthetic orders in {out}; benchmark={benchmark}")
    return files

if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--rows",type=int,default=100);p.add_argument("--out",default="sample-data/synthetic");p.add_argument("--prefix",default="SYN");p.add_argument("--benchmark",action="store_true");p.add_argument("--xlsx",action="store_true");args=p.parse_args()
    if not 1<=args.rows<=200000:p.error("--rows must be 1..200000")
    generate(args.out,args.rows,args.prefix,args.benchmark,args.xlsx)
