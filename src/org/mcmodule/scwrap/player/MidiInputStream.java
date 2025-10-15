package org.mcmodule.scwrap.player;

import java.io.*;

public class MidiInputStream extends DataInputStream {

    public MidiInputStream(RandomAccessFile file) throws IOException {
        this(new BufferedInputStream(new InputStream() {
            @Override
            public int read() throws IOException {
                return file.read();
            }

            public int read(byte b[], int off, int len) throws IOException {
                return file.read(b, off, len);
            }

            @Override
            public void close() throws IOException {
                file.close();
            }
        }, getBufferSize(file.length())));
    }

    private static int getBufferSize(long length) {
        if (length < 8192L) {
            return (int) length;
        }
        if (length < 1048576) {
            return 65536;
        }
        if (length < 10485460) {
            return 262144;
        }
        return 1048576;
    }

    public MidiInputStream(InputStream in) {
        super(in);
    }

    public final int readVarInt() throws IOException {
        int val = 0;
        int b;
        do {
            b = readUnsignedByte();
            if (b == -1) throw new EOFException();
            val <<= 7;
            val |= b & 0x7F;
        } while ((b & 0x80) != 0);
        return val;
    }

    public final long readVarLong() throws IOException {
        long val = 0;
        int b;
        do {
            b = readUnsignedByte();
            if (b == -1) throw new EOFException();
            val <<= 7;
            val |= b & 0x7F;
        } while ((b & 0x80) != 0);
        return val;
    }

    public int readVarInt(int firstByte) throws IOException {
        if (firstByte == -1) throw new EOFException();
        int val = firstByte & 0x7f;
        int b = firstByte;
        while ((b & 0x80) != 0) {
            b = readUnsignedByte();
            if (b == -1) throw new EOFException();
            val <<= 7;
            val |= b & 0x7F;
        }
        return val;
    }

}
