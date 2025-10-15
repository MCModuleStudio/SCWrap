package org.mcmodule.scwrap.util;

import java.io.IOException;
import java.io.RandomAccessFile;

public enum SCCoreVersion {
	Y2015_REV1_64BIT("Version 1.00 (Build 1000)"    , 0x9D5FD, 0x0A030000, 0x180000000L, 0x18006BA10L, 0x181A242E0L, 0x181A32A38L, 0x181A2BBF0L, 0x181A3299CL, 0x181A8B718L, 0x181A8AC56L),
	Y2017_REV1_64BIT("Version 1.11 (S) (Build 1110)", 0x8F560, 0x85DB74CC, 0x180000000L, 0x180060D70L, 0x181A23048L, 0x181A26730L, 0x181A23100L, 0x181A266A4L, 0x181A78618L, 0x181A77E2DL),
	Y2020_REV1_64BIT("Version 1.16 (S) (Build 1160)", 0x89FEC, 0x85DB74C0, 0x180000000L, 0x18005C5D0L, 0x181A1EF78L, 0x181A22660L, 0x181A1F030L, 0x181A225D8L, 0x181A74598L, 0x181A73DADL);

	private final String internalVersion;
	private final int identifyAddr;
	private final int identifyData;
	private final long baseOffset;
	private final long getPortLevelFunction;
	private final long blockBaseVariable;
	private final long eventBufferQueueVariable;
	private final long buttonQueueVariable;
	private final long isXGModeVariable;
	private final long eventFlagVariable;
	private final long mapVariable;

	SCCoreVersion(String internalVersion, int identifyAddr, int identifyData, long baseOffset, long getPortLevelFunction, long blockBaseVariable, long eventBufferQueueVariable, long buttonQueueVariable, long isXGModeVariable, long eventFlagVariable, long mapVariable) {
		this.internalVersion = internalVersion;
		this.identifyAddr = identifyAddr;
		this.identifyData = identifyData;
		this.baseOffset = baseOffset;
		this.getPortLevelFunction = getPortLevelFunction - baseOffset;
		this.blockBaseVariable = blockBaseVariable - baseOffset;
		this.eventBufferQueueVariable = eventBufferQueueVariable - baseOffset;
		this.buttonQueueVariable = buttonQueueVariable - baseOffset;
		this.isXGModeVariable = isXGModeVariable - baseOffset;
		this.eventFlagVariable = eventFlagVariable - baseOffset;		
		this.mapVariable = mapVariable - baseOffset;
	}
	
	public String getInternalVersion() {
		return this.internalVersion;
	}

	public int getidentifyAddr() {
		return this.identifyAddr;
	}

	public int getidentifyData() {
		return this.identifyData;
	}

	public long getBaseOffset() {
		return this.baseOffset;
	}

	public long getGetPortLevelFunction() {
		return this.getPortLevelFunction;
	}

	public long getBlockBaseVariable() {
		return this.blockBaseVariable;
	}

	public long getEventBufferQueueVariable() {
		return this.eventBufferQueueVariable;
	}

	public long getButtonQueueVariable() {
		return this.buttonQueueVariable;
	}

	public long getIsXGModeVariable() {
		return this.isXGModeVariable;
	}

	public long getEventFlagVariable() {
		return this.eventFlagVariable;
	}

	public long getMapVariable() {
		return this.mapVariable;
	}

	public static SCCoreVersion identifyVersion(String libraryPath) throws IOException {
		SCCoreVersion[] versions = values();
		try (RandomAccessFile file = new RandomAccessFile(libraryPath, "r")) {
			for (int i = 0, len = versions.length; i < len; i++) {
				SCCoreVersion version = versions[i];
				file.seek(version.identifyAddr);
				if (file.readInt() == version.identifyData) {
					return version;
				}
			}
		}
		return null;
	}
}
