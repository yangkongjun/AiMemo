#!/usr/bin/env python3
"""Check install-critical resource layout after APK signing. Also run apksigner verify."""
from pathlib import Path
from zipfile import ZipFile, ZIP_STORED
import argparse,struct

def check(path):
    path=Path(path)
    with ZipFile(path) as z:
        bad=z.testzip()
        if bad:raise ValueError('ZIP CRC error: '+bad)
        for name in ('AndroidManifest.xml','resources.arsc','classes.dex'):
            if name not in z.namelist():raise ValueError('Missing '+name)
        info=z.getinfo('resources.arsc')
        if info.compress_type!=ZIP_STORED:raise ValueError('resources.arsc is compressed; Android 11+ can reject installation')
        with path.open('rb') as f:
            f.seek(info.header_offset);header=f.read(30)
        if header[:4]!=b'PK\x03\x04':raise ValueError('Invalid local header')
        name_length,extra_length=struct.unpack_from('<HH',header,26)
        offset=info.header_offset+30+name_length+extra_length
        if offset%4:raise ValueError('resources.arsc is not 4-byte aligned')
        if z.read('AndroidManifest.xml')[:4]!=b'\x03\x00\x08\x00':raise ValueError('Manifest is not binary Android XML')
        return {'resources_uncompressed':True,'resources_data_offset':offset,'resources_alignment':4,'zip_crc_valid':True}

if __name__=='__main__':
    import json
    parser=argparse.ArgumentParser();parser.add_argument('apk');args=parser.parse_args()
    print(json.dumps(check(args.apk)))
