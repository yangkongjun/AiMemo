from pathlib import Path
from xml.etree import ElementTree as E
from zipfile import ZipFile,ZIP_DEFLATED,ZIP_STORED
import subprocess,os,struct,re
B=Path(__file__).resolve().parent
P=B.parent/'floatmemo-ai-android'
T=B/'tools/android-15'; A=B/'platform/android-35/android.jar'
O=B/'out';O.mkdir(exist_ok=True)
for f in (T/'aapt2',T/'zipalign'):
 f.chmod(0o755)
def run(*a):
 print('RUN',str(a[0]),flush=True);subprocess.run([str(x) for x in a],check=True)
ns='http://schemas.android.com/apk/res/android'
r=E.parse(P/'app/src/main/AndroidManifest.xml');root=r.getroot();root.set('package','io.github.floatmemo.android')
gradle=(P/'app/build.gradle').read_text()
version_code=re.search(r'versionCode\s+(\d+)',gradle).group(1)
version_name=re.search(r"versionName\s+'([^']+)'",gradle).group(1)
root.set('{'+ns+'}versionCode',version_code);root.set('{'+ns+'}versionName',version_name)
E.SubElement(root,'uses-sdk',{'{'+ns+'}minSdkVersion':'26','{'+ns+'}targetSdkVersion':'35'})
app=root.find('application');app.set('{'+ns+'}debuggable','true')
for x in app:
 k='{'+ns+'}name'
 if x.get(k,'').startswith('.'):x.set(k,'io.github.floatmemo.android'+x.get(k))
E.register_namespace('android',ns);manifest=O/'AndroidManifest.xml';r.write(manifest,encoding='utf-8',xml_declaration=True)
run(T/'aapt2','compile','--dir',P/'app/src/main/res','-o',O/'resources.zip')
(O/'generated').mkdir(exist_ok=True)
run(T/'aapt2','link','-o',O/'resources.apk','--manifest',manifest,'-I',A,'--java',O/'generated','--custom-package','io.github.floatmemo.android','--auto-add-overlay','-R',O/'resources.zip')
filtered=O/'android-api-only.jar'
with ZipFile(A) as src,ZipFile(filtered,'w',ZIP_DEFLATED) as z:
 for name in src.namelist():
  if name.endswith('.class') and not name.startswith(('java/','javax/','org/xml/','org/w3c/')):z.writestr(name,src.read(name))
sources=list((P/'app/src/main/java').rglob('*.java'))+list((O/'generated').rglob('*.java'))
args=O/'sources.txt';args.write_text('\n'.join(str(p) for p in sources))
run('java','-jar',B/'ecj.jar','-17','-encoding','UTF-8','-warn:none','-classpath',filtered,'-d',O/'classes','@'+str(args))
with ZipFile(O/'classes.jar','w',ZIP_DEFLATED) as z:
 for p in (O/'classes').rglob('*.class'):z.write(p,p.relative_to(O/'classes'))
(O/'dex').mkdir(exist_ok=True)
run('java','-cp',T/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',A,'--output',O/'dex',O/'classes.jar')
with ZipFile(O/'resources.apk') as source,ZipFile(O/'unsigned.apk','w',ZIP_DEFLATED) as z:
 for info in source.infolist():
  # Preserve AAPT2 storage choices. Android 11+ rejects compressed resources.arsc.
  if info.filename=='resources.arsc':info.compress_type=ZIP_STORED
  z.writestr(info,source.read(info.filename))
 for p in (O/'dex').glob('*.dex'):z.write(p,p.name)
run(T/'zipalign','-f','4',O/'unsigned.apk',O/'aligned.apk')
keystore=B/'preview-signing.keystore'
if not keystore.exists():raise RuntimeError('Existing signing key missing; stop rather than changing application identity')
release_tag=version_name.split('-')[0].replace('.','_')
final=B.parent/('deliverables/FloatMemo_AI_Android_V'+release_tag+'.apk')
if not all(os.environ.get(k) for k in ('FLOATMEMO_STORE_PASSWORD','FLOATMEMO_KEY_PASSWORD')):
 raise RuntimeError('Set private FLOATMEMO_STORE_PASSWORD and FLOATMEMO_KEY_PASSWORD environment variables before signing')
final.parent.mkdir(parents=True,exist_ok=True)
run('java','-jar',T/'lib/apksigner.jar','sign','--ks',keystore,'--ks-pass','env:FLOATMEMO_STORE_PASSWORD','--key-pass','env:FLOATMEMO_KEY_PASSWORD','--out',final,O/'aligned.apk')
run('java','-jar',T/'lib/apksigner.jar','verify','--verbose',final)
run(T/'zipalign','-c','4',final)
with ZipFile(final) as z:
 info=z.getinfo('resources.arsc')
 assert info.compress_type==ZIP_STORED,'resources.arsc must not be compressed'
 with final.open('rb') as f:
  f.seek(info.header_offset);local=f.read(30)
 name_length,extra_length=struct.unpack_from('<HH',local,26)
 offset=info.header_offset+30+name_length+extra_length
 assert offset%4==0,'resources.arsc must be 4-byte aligned'
 assert 'classes.dex' in z.namelist()
 print('INSTALL STRUCTURE PASS resources.arsc uncompressed, offset',offset,'aligned',offset%4==0)
# Run all existing JVM tests against the newly compiled classes, not old Gradle output.
tests=list((P/'app/src/test/java').rglob('*.java'));args=O/'test-sources.txt';args.write_text('\n'.join(str(p) for p in tests))
filtered=O/'android-api-only.jar'
with ZipFile(A) as src,ZipFile(filtered,'w',ZIP_DEFLATED) as z:
 for name in src.namelist():
  if name.endswith('.class') and not name.startswith(('java/','javax/','org/xml/','org/w3c/')):z.writestr(name,src.read(name))
cp=os.pathsep.join(str(p) for p in [O/'classes',P/'app/test-libs/json-20240303.jar',B/'junit.jar',B/'hamcrest.jar',filtered])
run('java','-jar',B/'ecj.jar','-17','-encoding','UTF-8','-warn:none','-classpath',cp,'-d',O/'test-classes','@'+str(args))
run('java','-cp',str(O/'test-classes')+os.pathsep+cp,'org.junit.runner.JUnitCore',*[('io.github.floatmemo.android.'+p.stem) for p in tests])
print(final)
