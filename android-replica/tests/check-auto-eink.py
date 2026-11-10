from pathlib import Path
root=Path(__file__).resolve().parents[1]
reader=(root/'src/com/example/yuejie/ReaderActivity.java').read_text(encoding='utf-8')
settings=(root/'src/com/example/yuejie/ReaderSettings.java').read_text(encoding='utf-8')
app=(root/'src/com/example/yuejie/ReadingDogApp.java').read_text(encoding='utf-8')
for key in ['eink_stable_override','eink_delayed_loading','eink_loading_delay','eink_fast','eink_periodic']:
 assert key not in reader+settings,key
for text in ['墨水屏实验','稳定显示已开启','showEinkExperiment','einkPageTurn']:
 assert text not in reader,text
assert 'EInkSupport' not in app and 'readerInitialized' not in app
options=reader.split('void pageTurnOptions(){',1)[1].split('void ',1)[0]
assert 'einkStable' not in options and 'PageTurnMode.values()' in options
assert 'settings.loadingNoticeDelay()' in reader
print('PASS automatic policy wiring, legacy preference isolation, identical settings and no hardware lifecycle calls (source checks)')
