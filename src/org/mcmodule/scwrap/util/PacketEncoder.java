package org.mcmodule.scwrap.util;

public class PacketEncoder {
	public static int encodeShortMessage(int cableNumber, int midiData) {
		int status = (midiData & 0xF0) >> 4;
		int header = (cableNumber & 0xF) << 4;
		
		if (status > 0x7 && status < 0xF) {
			header |= status;
		} else {
			if (status == 0xF) {
				switch (status) {
				case 0x0:
					if (((midiData & 0xFF00) >> 8) == 0xF7) {
						header |= 6;
					} else if (((midiData & 0xFF0000) >> 16) == 0xF7) {
						header |= 7;
					} else return 0;
					break;
				case 0x1:
				case 0x3:
					header |= 2;
					break;
				case 0x2:
					header |= 3;
					break;
				case 0x4:
				case 0x5:
				case 0x6:
					header |= 5;
					break;
				case 0x7:
					return 0;
				case 0x8:
				case 0x9:
				case 0xA:
				case 0xB:
				case 0xC:
				case 0xD:
				case 0xE:
				case 0xF:
					header |= 15;
					break;
				}
				return header | ((midiData & 0xFF) << 8) | (((midiData >> 8) & 0xFF) << 16) | (((midiData >> 16) & 0xFF) << 24);
			} else {
				return 0;
			}
		}
		
		return (header & 0xFF) | ((midiData & 0xFF) << 8) | (((midiData >> 8) & 0xFF) << 16) | (((midiData >> 16) & 0xFF) << 24);
	}
	
	public static int[] encodeLongMessage(int cableNumber, byte[] input, int off, int len) {
		if (input == null) return new int[0];
		
		int maxPackets = len / 3 + (len % 3 != 0 ? 1 : 0);
		
		int[] output = new int[maxPackets];

		int packetCount = 0;
		int header = (cableNumber & 0xF) << 4;
		int inputIndex = off;
		len += off;

		while (packetCount < maxPackets) {
			int b1 = 0, b2 = 0, b3 = 0;

			if (inputIndex < len) {
				b1 = input[inputIndex++] & 0xFF;
				if (b1 == 0xF7) {
					output[packetCount++] = (header | 5) | 0xF700;
					break;
				}

				if (inputIndex < len) {
					b2 = input[inputIndex++] & 0xFF;
					if (b2 == 0xF7) {
						output[packetCount++] = (header | 6) | (b1 << 8) | 0xF70000;
						break;
					}

					if (inputIndex < len) {
						b3 = input[inputIndex++] & 0xFF;
						if (b3 == 0xF7) {
							output[packetCount++] = (header | 7) | (b2 << 16) | (b1 << 8) | 0xF7000000;
							break;
						}

						output[packetCount++] = (header | 4) | (b3 << 24) | (b2 << 16) | (b1 << 8);
					} else break;
				} else break;
			} else break;
		}

		return output;
	}
}
