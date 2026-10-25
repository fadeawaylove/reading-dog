from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1];res=root/'res';(res/'drawable').mkdir(exist_ok=True);(res/'mipmap-anydpi-v26').mkdir(exist_ok=True)
bg='#365747';cream='#F5EEDD'
paths=[
 (cream,'M39 41 Q34 29 29 34 Q24 42 30 55 Q32 59 38 54 Z',None,0),
 (cream,'M69 41 Q74 29 79 34 Q84 42 78 55 Q76 59 70 54 Z',None,0),
 (cream,'M38 42 Q39 34 54 34 Q69 34 70 42 L70 53 Q68 66 54 66 Q40 66 38 53 Z',None,0),
 (bg,'M44 46 Q46 44 48 46 Q49 50 46 50 Q43 50 44 46 Z',None,0),
 (bg,'M60 46 Q62 44 64 46 Q65 50 62 50 Q59 50 60 46 Z',None,0),
 (bg,'M50 54 Q54 51 58 54 Q57 58 54 59 Q51 58 50 54 Z',None,0),
 (cream,'M27 60 Q40 57 54 65 Q68 57 81 60 L81 80 Q68 77 54 85 Q40 77 27 80 Z',None,0),
 (None,'M32 65 Q42 64 50 69 L50 77 Q42 73 32 74',bg,1.8),
 (None,'M76 65 Q66 64 58 69 L58 77 Q66 73 76 74',bg,1.8),
 (None,'M54 66 L54 80',bg,1.8),
]
def parse(path):
 tokens=re.findall('[MLQZ]|-?\\d+(?:\\.\\d+)?',path);out=[];i=0;current=(0,0);first=None
 while i<len(tokens):
  cmd=tokens[i];i+=1
  if cmd in ('M','L'):
   point=(float(tokens[i]),float(tokens[i+1]));i+=2
   if cmd=='M':first=point
   out.append(point);current=point
  elif cmd=='Q':
   cx,cy,x,y=map(float,tokens[i:i+4]);i+=4;ox,oy=current
   for n in range(1,21):
    t=n/20;out.append(((1-t)**2*ox+2*(1-t)*t*cx+t*t*x,(1-t)**2*oy+2*(1-t)*t*cy+t*t*y))
   current=(x,y)
  elif cmd=='Z':out.append(first);current=first
 return out
# render the same paths consumed by Android; all preview pixels are derived from vector geometry
im=Image.new('RGB',(864,864),bg);draw=ImageDraw.Draw(im)
for fill,path,stroke,width in paths:
 points=[(x*8,y*8) for x,y in parse(path)]
 if fill:draw.polygon(points,fill=fill)
 if stroke:draw.line(points,fill=stroke,width=round(width*8),joint='curve')
# Unmasked source as a shareable graphic; launcher masks are applied only to the preview.
im.save(root/'design/reading-dog-icon.png')
svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108"><rect width="108" height="108" fill="{bg}"/>'
for fill,path,stroke,width in paths:svg+=f'<path d="{path}" fill="{fill or "none"}" stroke="{stroke or "none"}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>'
svg+='</svg>';(root/'design/reading-dog-icon.svg').write_text(svg,encoding='utf-8')
def xmlvector(background=False):
 value='<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">'
 if background:value+=f'<path android:fillColor="{bg}" android:pathData="M0 0 H108 V108 H0 Z"/>'
 for fill,path,stroke,width in paths:
  value+=f'<path android:fillColor="{fill or "#00000000"}" android:pathData="{path}"'
  if stroke:value+=f' android:strokeColor="{stroke}" android:strokeWidth="{width}" android:strokeLineCap="round" android:strokeLineJoin="round"'
  value+='/>'
 return value+'</vector>'
(res/'drawable/ic_launcher.xml').write_text(xmlvector(True),encoding='utf-8');(res/'drawable/ic_launcher_foreground.xml').write_text(xmlvector(),encoding='utf-8')
(res/'drawable/ic_launcher_background.xml').write_text(f'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="{bg}"/></shape>',encoding='utf-8')
(res/'mipmap-anydpi-v26/ic_launcher.xml').write_text('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/ic_launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/></adaptive-icon>',encoding='utf-8')
# Legacy resolution resolves the same name on pre-26 devices.
(res/'mipmap-anydpi').mkdir(exist_ok=True);(res/'mipmap-anydpi/ic_launcher.xml').write_text('<inset xmlns:android="http://schemas.android.com/apk/res/android" android:drawable="@drawable/ic_launcher" android:inset="0dp"/>',encoding='utf-8')
preview=Image.new('RGB',(1080,520),'#f5f1e8');d=ImageDraw.Draw(preview);font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',24);small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',17)
d.text((40,30),'阅读狗 · 翻书的小狗',font=font,fill='#383a33')
for i,shape in enumerate(['square','circle','small']):
 size=280 if shape!='small' else 96;x=[55,420,850][i];y=112 if shape!='small' else 195
 icon=im.resize((size,size),Image.Resampling.LANCZOS);mask=Image.new('L',(size,size));m=ImageDraw.Draw(mask)
 if shape=='circle':m.ellipse((0,0,size-1,size-1),fill=255)
 else:m.rounded_rectangle((0,0,size-1,size-1),radius=size*.23,fill=255)
 preview.paste(icon,(x,y),mask);d.text((x,y+size+24),['圆角桌面','圆形桌面','小尺寸'][i],font=small,fill='#929287')
preview.save(root/'design/icon-preview.png');print('Vector icon and Android adaptive/legacy assets written')
