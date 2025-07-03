# SCWrap
SCWrap wraps Roland's SCCore.dll from the Sound Canvas VA plugin. It restores support for 2 MIDI ports and XG mode by patching code.

## Usage
```
  -r, --rate       <sample rate>   Set sample rate (default: 32000)
  -s, --block       <block size>   Set block size (default: 128)
  -l, --lib       <library path>   Set SCCore.dll library path (default: SCCore.dll)
  -a, --midiA, -midi <port name>   Set MIDI input A
  -b, --midiB        <port name>   Set MIDI input B
```

## Multi-instance
SCCore.dll does not natively support multiple instances, But your can copy dll to temporary folder and then load it. This is the same method used internally by Roland Sound Canvas VA itself.

## Supported SCCore.dll
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