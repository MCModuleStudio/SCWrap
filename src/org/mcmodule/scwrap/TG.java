package org.mcmodule.scwrap;


import com.sun.jna.Library;
import com.sun.jna.Pointer;

public interface TG extends Library {
	void TG_Process(Pointer left, Pointer right, int length);
	
	void TG_LongMidiIn(Pointer msg, int ts);
	
	void TG_PMidiIn(int msg, int ts);
	
	void TG_ShortMidiIn(int msg, int ts);
	
	void TG_XPgetCurSystemConfig(Pointer config);
	
	int TG_XPgetCurTotalRunningVoices();
	
	long TG_XPsetSystemConfig(Pointer config);
	
	int TG_activate(float sampleRate, int blockSize);
	
	int TG_deactivate();
	
	void TG_flushMidi();
	
	String TG_getErrorStrings(int errcode);
	
	int TG_initialize(int mode);
	
	default boolean TG_isFatalError(int errcode) {
		return errcode >>> 31 != 0;
	}
	
	int TG_setMaxBlockSize(int blockSize);
	
	int TG_setSampleRate(float sampleRate);
	
}
