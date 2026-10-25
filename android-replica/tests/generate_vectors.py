"""Create comparison vectors from retained native captures and verified Python code."""
import sys,json,pathlib,base64,hashlib
root=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(root/'research/tomato-book-independent/standalone'))
import argus,ladon,content_crypto
out=pathlib.Path(__file__).parent/'vectors.tsv'
rows=[]
for p in [root/'research/tomato-independent/baseline/captured-request.json',root/'research/tomato-book-independent/native-capture/captured-request.json']:
 d=json.loads(p.read_text(encoding='utf-8'))
 for q,h in zip(d['queries'],d['signatures']):
  ts=int(h['X-Khronos']);rng=argus.decode_argus(h['X-Argus'])['fields'][3]
  rows.append(['argus',base64.b64encode(q.encode()).decode(),str(ts),str(rng),h['X-Argus']])
  for k in ['X-Ladon','X-Helios']:
   if k in h: rows.append(['ladon',str(ts),base64.b64decode(h[k])[:4].hex(),h[k]])
rows.append(['sm3','abc','66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0'])
rows.append(['register','4122540637687240',bytes(range(16)).hex(),content_crypto.build_register_content('4122540637691336','4122540637687240',bytes(range(16)))])
evidence=root/'research/tomato-book-independent/content-evidence'
register=json.loads((evidence/'1792848136221-registerkey.json').read_text(encoding='utf-8'))
key=content_crypto.decrypt_register_key(register['response']['data']['key'])
rows.append(['key',register['response']['data']['key'],key.hex()])

key=None
for f in sorted(evidence.glob('*.json')):
 if '-registerkey' in f.name:
  d=json.loads(f.read_text(encoding='utf-8'))
  try:key=content_crypto.decrypt_register_key(d['response']['data']['key'])
  except Exception:key=None
 elif '-batch-full' in f.name and key:
  d=json.loads(f.read_text(encoding='utf-8'))
  for cid,item in d.get('response',{}).get('data',{}).items():
   if not isinstance(item,dict) or not item.get('content'):continue
   try:clear=content_crypto.decrypt_chapter_content(item['content'],key)
   except Exception:continue
   rows.append(['chapter',item['content'],key.hex(),hashlib.sha256(clear.encode()).hexdigest()])
expected={f.stem:hashlib.sha256(f.read_text(encoding='utf-8').encode()).hexdigest() for f in evidence.glob('*.txt')}
(pathlib.Path(__file__).parent/'expected-text.json').write_text(json.dumps(expected,indent=2),encoding='utf-8')
out.write_text('\n'.join('\t'.join(r) for r in rows),encoding='utf-8')
print('vectors:',len(rows),'chapter:',sum(r[0]=='chapter' for r in rows))
