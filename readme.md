# SCWrap
SCWrap wraps Roland's SCCore.dll from the Sound Canvas VA plugin. It restores support for 2 MIDI ports and XG mode by patching code.

## Usage
```
  -r, --rate            <sample rate>   Set sample rate (default: 32000)
  -s, --block            <block size>   Set block size (default: 128)
  -l, --lib            <library path>   Set SCCore.dll library path (default: SCCore.dll)
  -a, --midiA, -midi <port name/file>   Set MIDI input A
  -b, --midiB        <port name/file>   Set MIDI input B
  -p, --midiOut           <port name>   Set MIDI output
  -o, --output            <file name>   Set audio output file
  -m, --map                <map type>   Set map type
      --gui                             Open gui
```

## Multi-instance
SCCore.dll does not natively support multiple instances, But your can copy dll to temporary folder and then load it. This is the same method used internally by Roland Sound Canvas VA itself.

## About GUI and MIDI Output

Since both the GUI and MIDI output require access to internal variables of SCCore, only three versions are supported: **2015 version (64-bit)**, **2017 version (64-bit)**, and **2020 version (64-bit)**.

The **2016 version (64-bit)** will be recognized as the **2017 version (64-bit)** and will work normally, because aside from differences in compilation timestamps and some data in the .rdata section, the .text section is completely identical between the two.

## Supported SCCore.dll
## 2015 version (64bit)
```
Size: 27436032 Bytes : 26 MiB
CRC32: D4206EEA
CRC64: 034876B5BE60430D
SHA256: c87b6c0617d28a60de8b0908d7c1b830825b4c6b798789497a069606b3a464df
SHA1: d12d969b69c9d361a1ccf69805275667ecf1d4bb
BLAKE2sp: 496eae4b6bfc76eadc1d575d9120871467cf2428d500b3869cf0038303fb2d3b
MD5: ae88ac4f4bf8ef3fbcb83a56ebaafc02
XXH64: 5777456B7F32C7F0
SHA384: d8cd9dfd1a02375490aad706966e47757a8d3431a56e059f504ce3beaa99aa2d109c9a27c9b4b8802899fbcfbab58dff
SHA512: da765db8d1b4dbbe021727894fc00fd6a225be52dffc20881f18e31b384672a8932213ae136659b278ebdf275c5c51552b02455c83ec89972fe33862d31d3ac3
SHA3-256: ba033a891dd3ad002b5beed79c39f44d90a5fb764a55b45ff5b17897458d6952

```
### 2016 version (32bit, untested)
```
Size: 27472384 Bytes : 26 MiB
CRC32: 4FE4AA09
CRC64: B9EDD98C5C0B0F86
SHA256: 8ddb29d5e1c20ba5a262084e96fbc4f4de8c70db988873b80a1a6bd3ee244f29
SHA1: 41911c21d7e1d6da5574cb42c35c7ba96ea110e0
BLAKE2sp: c287faee5b372bbb3eb932554fc5e8f917b9a146a6e22f4866ee37beb67c3064
MD5: d44d1b8c9a6f956ca2324f2f5d348c44
XXH64: A27E0D3D6FE95B56
SHA384: 63da20163f379e97202af0da913cb1c4759bda8f0db1b19f177b84fb2a8ca35d99247b0147c573d9475b35d498cba1f3
SHA512: 0b48f48e53e931cbabb053b51d0448a533aeae69c07be0a082050b376dc1667406d9fb0f73529c74f992d6e727f8a814d4be56bfab1c7d636d53eebc6a6ee10c
SHA3-256: b65203b28c50fffe2c31390c79d433ddfffdacb933c35c28269ca9f810a24406
```
### 2016 version (64bit)
```
Size: 27358208 Bytes : 26 MiB
CRC32: 700EDAA0
CRC64: D59AEF1C87A6A0DF
SHA256: f8b0dbedfb683c581f76f68105864b4365f33005fd369f486daf4d1c5b2414e1
SHA1: e87d9ae914a07b29a7bde4a0b7e2b9f1bea77b67
BLAKE2sp: 387f69f3f7438ae1f2fdc69a911640d12bbd152ea39796f1e7c5f45eacad053e
MD5: 6abfbf61869fc436d76c93d1bc7e2735
XXH64: 46CF517ECA8150F8
SHA384: a1ad12215fdf364dddda556959d930151387599db56e240c7f6b3eec84328f5277b9e2a8e8801959a434210d33a3bbc0
SHA512: 5ab2d0accdb278f913a2151d1f8f1cf78696468eb685ced2fc799eda922c72936ed819077c34d90f7783993e77d536f643ad5c71043eb7132378bf0d753b29e2
SHA3-256: b8d50761a4d44c713e4211d90e0ff5e4be324ead44285235ef5f69dc0f65abb7

```
### 2017 version (64bit)
```
Size: 27358208 Bytes : 26 MiB
CRC32: 505B876F
CRC64: AEFCCBBCDE35B26B
SHA256: 0635cc2bfced7876694f362f29719bae58e4539d576af9321673f6ffc31f6735
SHA1: ba0d868fd55b3114ce2cfb7dec0a5f6314113791
BLAKE2sp: 8298c8a162b5f9cc55ef47689a0419932cad6f8501eaa5c289f26d32561a6f4d
MD5: 80f1e673d249d1cda67a2936326f866b
XXH64: A0FD46F93F683C97
SHA384: 99e6136d839e3d97838f0b3502ebb002367db5cd94ce719a62f9f50422fcb63a0f10a47460d690204c23158cf49ddd01
SHA512: 6172129715ae41b0dc158e0a22c064f70a1a250f5a6202548d5ea192347b77a650072a9185630c07f96dc0906553289e630767634f4d80d148bf561a110b3577
SHA3-256: ae39747dc18d807f270dc3d6138aae014b5ef21938a1f2daa4884a7336aab58b
```
### 2020 version (64bit)
```
Size: 27347456 Bytes : 26 MiB
CRC32: 43063D74
CRC64: 5BCECE00DB88A88D
SHA256: 117e6aa147a96fbde5e10d2caf16c89965acc1e44235fd245992216cc620bdb1
SHA1: cf9dce5a0cabee06792e884673b8beef806f1aed
BLAKE2sp: 9a0baaa677568181ba29f7c54c10cec803c9122a4c21cad329c98e045d756bda
MD5: dbd9a30c168efef577d40a28d9adf37d
XXH64: 2CB50DE0DAC15DF9
SHA384: f7aa90576df6679dbe71c8d9eb29a04d8b8ef732f31701e551659afc8288184129d3c14fb7ca374e8bb2b9df27de6782
SHA512: 8630cb73146b64fbddabd9e4316257a365f57e6242cdb586d7bf78adc8a55699107cd708f8442d3a720ee17662bb9f13fe35bd5ce2250b9d82d0efe51e4e34dc
SHA3-256: 5b3abbaf3c9bb7c9049ba0338f48ac698de05d09a2fda441a03fac7d454c1132
```