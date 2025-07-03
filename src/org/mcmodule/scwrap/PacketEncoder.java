package org.mcmodule.scwrap;

public class PacketEncoder {

	/**
	 * 将 MIDI 短消息转换为MP-MIDI格式
	 * 
	 * @param cableNumber 电缆编号 (0~15)
	 * @param midiData	  32位整数，低3字节为MIDI消息
	 * @return			  编码后的数据，0为无效
	 */
	public static int encodeShortMessage(int cableNumber, int midiData) {

		int status = (midiData & 0xF0) >> 4;
		byte header = (byte) (cableNumber << 4);
		
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
				return (header & 0xFF) | ((midiData & 0xFF) << 8) | (((midiData >> 8) & 0xFF) << 16) | (((midiData >> 16) & 0xFF) << 24);
			} else {
				return 0; // Invalid
			}
		}
		
		return (header & 0xFF) | ((midiData & 0xFF) << 8) | (((midiData >> 8) & 0xFF) << 16) | (((midiData >> 16) & 0xFF) << 24);
	}
	
	/**
	 * 将 MIDI SysEx 长消息转换为 MP-MIDI 多个4字节包
	 *
	 * @param cableNumber 电缆编号 (0~15)
	 * @param input	      输入的 SysEx 数据
	 * @return			  编码后的数据，0为无效
	 */
	public static int[] encodeLongMessage(int cableNumber, byte[] input, int off, int len) {
		if (input == null) return new int[0];
		
		int maxPackets = len / 3 + (len % 3 != 0 ? 1 : 0);
		
		int[] output = new int[maxPackets];

		int packetCount = 0;
		byte headerBase = (byte) (cableNumber << 4);
		int inputIndex = off;

		while (packetCount < maxPackets) {
			byte b1 = 0, b2 = 0, b3 = 0;

			// 获取最多三个数据字节
			if (inputIndex < len) {
				b1 = input[inputIndex++];
				if (b1 == (byte) 0xF7) { // End1
					output[packetCount++] = (headerBase | 5) | 0xF700;
					break;
				}

				if (inputIndex < len) {
					b2 = input[inputIndex++];
					if (b2 == (byte) 0xF7) { // End2
						output[packetCount++] = (headerBase | 6) | ((b1 & 0xFF) << 8) | 0xF70000;
						break;
					}

					if (inputIndex < len) {
						b3 = input[inputIndex++];
						if (b3 == (byte) 0xF7) { // End3
							output[packetCount++] = (headerBase | 7) | ((b2 & 0xFF) << 16) | ((b1 & 0xFF) << 8) | 0xF7000000;
							break;
						}

						// SysEx Continue Packet
						output[packetCount++] = (headerBase | 4) | ((b3 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b1 & 0xFF) << 8);
					} else {
						// 不足3个字节，但没遇到F7，无法处理，退出
						break;
					}
				} else {
					// 不足2个字节，但没遇到F7，无法处理，退出
					break;
				}
			} else {
				break;
			}
		}

		return output;
	}
}
