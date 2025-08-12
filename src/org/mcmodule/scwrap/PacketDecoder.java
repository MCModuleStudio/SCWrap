package org.mcmodule.scwrap;

import java.util.Arrays;

public class PacketDecoder {

	private byte[] buffer = new byte[4096];
	private int offset = 0;
	
	public byte[] decodeMessage(int input) {
		int header = input & 0xF;
		input >>>= 8;
		if (header > 8 && header < 0xF) {
			int length = (header == 0xC || header == 0xD) ? 2 : 3;
			byte[] result = new byte[length];
			for (int i = 0; i < length; i++) {
				result[i] = (byte) (input & 0xFF);
				input >>>= 8;
			}
			return result;
		}
		if (header == 4) {
			for (int i = 0; i < 3; i++) {
				this.buffer[this.offset++] = (byte) (input & 0xFF);
				input >>>= 8;
			}
			return null;
		}
		if (header > 4 && header < 8) {
			int length = 1;
			switch (header) {
			case 5:
				length = 1;
				break;
			case 6:
				length = 2;
				break;
			case 7:
				length = 3;
				break;
			}
			for (int i = 0; i < length; i++) {
				this.buffer[this.offset++] = (byte) (input & 0xFF);
				input >>>= 8;
			}
			int len = this.offset;
			this.offset = 0;
			return Arrays.copyOf(this.buffer, len);
		}
		this.offset = 0;
		return null;
	}
	
}
