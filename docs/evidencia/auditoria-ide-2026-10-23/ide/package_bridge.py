from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parent
manifest = '''<?xml version="1.0" encoding="utf-8"?>
<PackageManifest Version="2.0.0" xmlns="http://schemas.microsoft.com/developer/vsx-schema/2011">
<Metadata><Identity Language="en-US" Id="rutafija-local-qa-observer" Version="0.0.1" Publisher="rutafija-local"/>
<DisplayName>Ruta Fija Local QA Observer (temporary)</DisplayName><Description xml:space="preserve">Local diagnostic observer</Description>
<Tags/><Categories>Other</Categories><GalleryFlags>Public</GalleryFlags>
<Properties><Property Id="Microsoft.VisualStudio.Code.Engine" Value="^1.100.0"/></Properties></Metadata>
<Installation><InstallationTarget Id="Microsoft.VisualStudio.Code"/></Installation><Dependencies/>
<Assets><Asset Type="Microsoft.VisualStudio.Code.Manifest" Path="extension/package.json" Addressable="true"/></Assets></PackageManifest>'''
with ZipFile(root / 'qa-observer.vsix', 'w', ZIP_DEFLATED) as archive:
    archive.writestr('extension.vsixmanifest', manifest)
    archive.writestr('[Content_Types].xml', '''<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="json" ContentType="application/json"/><Default Extension="js" ContentType="application/javascript"/><Default Extension="vsixmanifest" ContentType="text/xml"/></Types>''')
    for name in ['package.json', 'extension.js']:
        archive.write(root / 'bridge' / name, f'extension/{name}')
print('Packaged local observer')
