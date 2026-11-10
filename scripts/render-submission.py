"""Render editable submission Markdown and package only the two permitted files."""
from pathlib import Path
import re, sys, zipfile, json
from xml.sax.saxutils import escape
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'.local/pdf-tools'))
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib import colors
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
import fitz

out=ROOT/'submission';out.mkdir(exist_ok=True)
source=ROOT/'docs/submission/Technical_Design_Document.md'
content=source.read_text(encoding='utf-8')
assert 'CURRENT_VERIFICATION' not in content,'Complete actual verification first'
pdfmetrics.registerFont(TTFont('Arial','C:/Windows/Fonts/arial.ttf'))
pdfmetrics.registerFont(TTFont('ArialBold','C:/Windows/Fonts/arialbd.ttf'))
styles=getSampleStyleSheet()
styles.add(ParagraphStyle(name='BodyA',fontName='Arial',fontSize=9.5,leading=14,spaceAfter=8,textColor=colors.HexColor('#27332e')))
styles.add(ParagraphStyle(name='TitleA',fontName='ArialBold',fontSize=22,leading=28,spaceAfter=18,textColor=colors.HexColor('#176344')))
styles.add(ParagraphStyle(name='HeadA',fontName='ArialBold',fontSize=13,leading=17,spaceBefore=12,spaceAfter=9,textColor=colors.HexColor('#176344'),keepWithNext=True))
styles.add(ParagraphStyle(name='CellA',fontName='Arial',fontSize=8.4,leading=11,spaceAfter=0))
story=[]
def para(s,style='BodyA'):
    return Paragraph(escape(s),styles[style])
blocks=re.split(r'\n\s*\n',content.strip())
for block in blocks:
    if block.startswith('<!-- pagebreak'):story.append(PageBreak())
    elif block.startswith('# '):story.append(para(block[2:],'TitleA'))
    elif block.startswith('## '):story.append(para(block[3:],'HeadA'))
    elif block.startswith('|'):
        rows=[]
        for line in block.splitlines():
            if re.match(r'^\|[\s:|\-]+$',line):continue
            rows.append([para(x.strip(),'CellA') for x in line.strip('|').split('|')])
        table=Table(rows,colWidths=[135,364],repeatRows=1,hAlign='LEFT')
        table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#e4eee8')),('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7),('TOPPADDING',(0,0),(-1,-1),6),('BOTTOMPADDING',(0,0),(-1,-1),6),('LINEBELOW',(0,0),(-1,-1),.35,colors.HexColor('#d6ded9'))]))
        story.extend([table,Spacer(1,10)])
    else:story.append(para(' '.join(block.splitlines())))
def footer(c,doc):
    c.setStrokeColor(colors.HexColor('#cbd8cf'));c.line(48,42,547,42)
    c.setFont('Arial',8);c.setFillColor(colors.HexColor('#5d6b62'))
    c.drawString(48,29,'Reconflow | Aryan Raj | Technical Design')
    c.drawRightString(547,29,str(doc.page))
pdf=out/'Technical_Design_Document.pdf'
SimpleDocTemplate(str(pdf),pagesize=(595.28,841.89),leftMargin=48,rightMargin=48,topMargin=40,bottomMargin=56,title='Reconflow - Technical Design Document',author='Aryan Raj').build(story,onFirstPage=footer,onLaterPages=footer)
render=ROOT/'tmp/pdfs/submission';render.mkdir(parents=True,exist_ok=True)
document=fitz.open(pdf)
for i,page in enumerate(document):page.get_pixmap(matrix=fitz.Matrix(1.2,1.2)).save(render/f'page-{i+1}.png')
text='\n'.join(p.get_text() for p in document)
assert 'CURRENT_VERIFICATION' not in (out/'deployment.md').read_text(encoding='utf-8')
# Compare exact private values in memory; never print them.
combined=text+(out/'deployment.md').read_text(encoding='utf-8')
for envfile in [ROOT/'.env',ROOT/'.local/reviewer.env']:
    if envfile.exists():
        for line in envfile.read_text(encoding='utf-8-sig').splitlines():
            if '=' not in line or line.startswith('#'):continue
            k,v=line.split('=',1)
            if len(v)>=8 and ('KEY' in k or 'PASSWORD' in k) and not (envfile.name=='reviewer.env' and k.startswith('DEMO_')):
                assert v not in combined,'Private credential found in deliverables'
archive=out/'Aryan_Raj_Isocodes_Submission.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for name in ['Technical_Design_Document.pdf','deployment.md']:z.write(out/name,name)
with zipfile.ZipFile(archive) as z:
    assert z.namelist()==['Technical_Design_Document.pdf','deployment.md']
    assert z.testzip() is None
assert archive.stat().st_size<25_000_000
print(json.dumps({'pages':len(document),'zip_bytes':archive.stat().st_size,'entries':['Technical_Design_Document.pdf','deployment.md'],'private_secret_scan':'PASS','rendered_pages':str(render)},indent=2))
