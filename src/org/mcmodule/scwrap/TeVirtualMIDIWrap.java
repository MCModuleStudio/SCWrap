package org.mcmodule.scwrap;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;

public class TeVirtualMIDIWrap implements AutoCloseable, Receiver {

	@SuppressWarnings("unused")
	private static final URLClassLoader classLoader;
	private static final boolean IS_SUPPORTED;
	private static Class<?> TeVirtualMIDIClass;
	private static Constructor<?> TeVirtualMIDIConstructor;
	private static Method sendCommandMethod;
	private static Method getCommandMethod;
	private static Method shutdownMethod;
	
	private Object instance;
	
	private TeVirtualMIDIWrap(Object instance) {
		this.instance = instance;
	}
	
	@Override
	public void send(MidiMessage message, long timeStamp) {
		sendCommand(message.getMessage());
	}
	
	public void sendCommand(byte[] command) {
		try {
			sendCommandMethod.invoke(this.instance, command);
		} catch (IllegalAccessException | IllegalArgumentException e) {
			throw new Error(e);
		} catch (InvocationTargetException e) {
			Throwable targetException = e.getTargetException();
			if (targetException instanceof RuntimeException) {
				throw (RuntimeException) targetException;
			} else {
				throw new Error(targetException);
			}
		}
	}
	
	public byte[] getCommand() {
		try {
			return (byte[]) getCommandMethod.invoke(this.instance);
		} catch (IllegalAccessException | IllegalArgumentException e) {
			throw new Error(e);
		} catch (InvocationTargetException e) {
			Throwable targetException = e.getTargetException();
			if (targetException instanceof RuntimeException) {
				throw (RuntimeException) targetException;
			} else {
				throw new Error(targetException);
			}
		}
	}

	public void close() {
		try {
			shutdownMethod.invoke(this.instance);
		} catch (IllegalAccessException | IllegalArgumentException e) {
			throw new Error(e);
		} catch (InvocationTargetException e) {
			Throwable targetException = e.getTargetException();
			if (targetException instanceof RuntimeException) {
				throw (RuntimeException) targetException;
			} else {
				throw new Error(targetException);
			}
		}
	}
	
	public static TeVirtualMIDIWrap createVirtualMidiPort(String name, int flags) {
		try {
			return new TeVirtualMIDIWrap(TeVirtualMIDIConstructor.newInstance(name, 1024, flags));
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
			throw new Error(e);
		} catch (InvocationTargetException e) {
			Throwable targetException = e.getTargetException();
			if (targetException instanceof RuntimeException) {
				throw (RuntimeException) targetException;
			} else {
				throw new Error(targetException);
			}
		}
	}
	
	// Initialize then check support
	public static boolean isSupported() {
		return IS_SUPPORTED;
	}
	
	static {
		boolean isSupported = false;
		URLClassLoader cl = null;
		try {
			// Hard coded URL path
			cl = new URLClassLoader(new URL[] {new File("C:\\Program Files (x86)\\Tobias Erichsen\\teVirtualMIDISDK\\Java-Binding\\lib\\tevirtualmidi.jar").toURI().toURL(), new File("C:\\Program Files\\Tobias Erichsen\\teVirtualMIDISDK\\Java-Binding\\lib\\tevirtualmidi.jar").toURI().toURL()}, TeVirtualMIDIWrap.class.getClassLoader());
			TeVirtualMIDIClass = cl.loadClass("de.tobiaserichsen.tevm.TeVirtualMIDI");
			TeVirtualMIDIConstructor = TeVirtualMIDIClass.getConstructor(String.class, int.class, int.class);
			sendCommandMethod = TeVirtualMIDIClass.getMethod("sendCommand", byte[].class);
			getCommandMethod = TeVirtualMIDIClass.getMethod("getCommand");
			shutdownMethod = TeVirtualMIDIClass.getMethod("shutdown");
			System.out.printf("teVirtualMIDI version: %s driver version : %s\n", TeVirtualMIDIClass.getMethod("getVersionString").invoke(null).toString(), TeVirtualMIDIClass.getMethod("getDriverVersionString").invoke(null).toString());
			isSupported = getCommandMethod.getReturnType() == byte[].class;
		} catch (ClassNotFoundException e) {
			
		} catch (Throwable t) {
			t.printStackTrace();
		}
		IS_SUPPORTED = isSupported;
		classLoader = cl;
	}
	
}
