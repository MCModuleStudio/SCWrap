package org.mcmodule.scwrap.gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

public class CharacterRenderer {
	
	private static final HashMap<Character, Long> characterData = new HashMap<>();
	private static final int FONT_WIDTH  = 50,
			 				 FONT_HEIGHT = 70;
	private final HashMap<Character, BufferedImage> textures = new HashMap<>();
	private Color backgroundColor, offColor, onColor;
	private BufferedImage unknownCharacter;

	public void setColor(Color backgroundColor, Color offColor, Color onColor) {
		this.backgroundColor = backgroundColor;
		this.offColor = offColor;
		this.onColor = onColor;
		regenerateCharacterTexture();
	}
	
	public void drawCharacter(int x, int y, char chr, Graphics2D g, ImageObserver observer) {
		BufferedImage img = this.textures.getOrDefault(chr, this.unknownCharacter);
		if (img == null) return;
		g.drawImage(img, x, y, observer);
	}

	private void regenerateCharacterTexture() {
		this.textures.clear();
		Iterator<Entry<Character, Long>> iter = characterData.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<Character, Long> entry = iter.next();
			long bitmap = entry.getValue();
			BufferedImage texture = new BufferedImage(FONT_WIDTH, FONT_HEIGHT, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = (Graphics2D) texture.getGraphics();
			g.setColor(this.backgroundColor);
			g.fillRect(0, 0, FONT_WIDTH, FONT_HEIGHT);
			for (int x = 0; x < 5; x++) {
				int col = (int) ((bitmap >> (7 * (4 - x))) & 0x7f);
				for (int y = 0; y < 7; y++) {
					boolean on = (col & (1 << y)) != 0;
					g.setColor(on ? this.onColor : this.offColor);
					g.fillRect(x * 10, y * 10, 8, 8);
				}
			}
			g.dispose();
			this.textures.put(entry.getKey(), texture);
		}
		this.unknownCharacter = this.textures.get('\ufffd');
	}
	
	static {
		characterData.put(' ',   0x0L);
		characterData.put('!',   0x17c000L);
		characterData.put('"',   0xe00380L);
		characterData.put('#',   0x14fe53f94L);
		characterData.put('$',   0x2455fd512L);
		characterData.put('%',   0x232623262L);
		characterData.put('&',   0x369355150L);
		characterData.put('\'',  0xa0c000L);
		characterData.put('(',   0x388a080L);
		characterData.put(')',   0x8288e00L);
		characterData.put('*',   0x1410f8414L);
		characterData.put('+',   0x810f8408L);
		characterData.put(',',   0xa0c0000L);
		characterData.put('-',   0x81020408L);
		characterData.put('.',   0xc180000L);
		characterData.put('/',   0x202020202L);
		characterData.put('0',   0x3ea3262beL);
		characterData.put('1',   0x85fe000L);
		characterData.put('2',   0x42c3464c6L);
		characterData.put('3',   0x2183165b1L);
		characterData.put('4',   0x18284bf90L);
		characterData.put('5',   0x278b162b9L);
		characterData.put('6',   0x3c95264b0L);
		characterData.put('7',   0x1e224283L);
		characterData.put('8',   0x3693264b6L);
		characterData.put('9',   0x6932549eL);
		characterData.put(':',   0x6cd8000L);
		characterData.put(';',   0xacd8000L);
		characterData.put('<',   0x1051141L);
		characterData.put('=',   0x142850a14L);
		characterData.put('>',   0x414450400L);
		characterData.put('?',   0x20344486L);
		characterData.put('@',   0x3293e60beL);
		characterData.put('A',   0x7e22448feL);
		characterData.put('B',   0x7f93264b6L);
		characterData.put('C',   0x3e83060a2L);
		characterData.put('D',   0x7f830511cL);
		characterData.put('E',   0x7f93264c1L);
		characterData.put('F',   0x7f1224481L);
		characterData.put('G',   0x3e83264faL);
		characterData.put('H',   0x7f102047fL);
		characterData.put('I',   0x83fe080L);
		characterData.put('J',   0x208105f81L);
		characterData.put('K',   0x7f1051141L);
		characterData.put('L',   0x7f8102040L);
		characterData.put('M',   0x7f043017fL);
		characterData.put('N',   0x7f082087fL);
		characterData.put('O',   0x3e83060beL);
		characterData.put('P',   0x7f1224486L);
		characterData.put('Q',   0x3e83450deL);
		characterData.put('R',   0x7f12654c6L);
		characterData.put('S',   0x4693264b1L);
		characterData.put('T',   0x103fc081L);
		characterData.put('U',   0x3f810203fL);
		characterData.put('V',   0x1f410101fL);
		characterData.put('W',   0x3f80e203fL);
		characterData.put('X',   0x632820a63L);
		characterData.put('Y',   0x309e0203L);
		characterData.put('Z',   0x61a3262c3L);
		characterData.put('[',   0x1fe0c1L);
		characterData.put('\\',  0x20820820L);
		characterData.put(']',   0x4183fc000L);
		characterData.put('^',   0x40404104L);
		characterData.put('_',   0x408102040L);
		characterData.put('`',   0x208200L);
		characterData.put('a',   0x20a952a78L);
		characterData.put('b',   0x7f9112238L);
		characterData.put('c',   0x388912220L);
		characterData.put('d',   0x38891247fL);
		characterData.put('e',   0x38a952a18L);
		characterData.put('f',   0x8fc24082L);
		characterData.put('g',   0xca54a93eL);
		characterData.put('h',   0x7f1010278L);
		characterData.put('i',   0x89f6000L);
		characterData.put('j',   0x208111e80L);
		characterData.put('k',   0x7f20a2200L);
		characterData.put('l',   0x83fe000L);
		characterData.put('m',   0x7c0860278L);
		characterData.put('n',   0x7c1010278L);
		characterData.put('o',   0x388912238L);
		characterData.put('p',   0x7c2850a08L);
		characterData.put('q',   0x82850c7cL);
		characterData.put('r',   0x7c1010208L);
		characterData.put('s',   0x48a952a20L);
		characterData.put('t',   0x47f12020L);
		characterData.put('u',   0x3c810107cL);
		characterData.put('v',   0x1c410101cL);
		characterData.put('w',   0x3c80c203cL);
		characterData.put('x',   0x445041444L);
		characterData.put('y',   0xca14283cL);
		characterData.put('z',   0x44c952644L);
		characterData.put('{',   0x10da080L);
		characterData.put('|',   0x1fc000L);
		characterData.put('}',   0x82d8400L);
		characterData.put('~',   0x101020808L);
		characterData.put('¥',   0x152df0b15L);
		characterData.put('±',   0x44897e244L);
		characterData.put('Ⅱ',    0x41ff07fc1L);
		characterData.put('←',   0x838a8408L);
		characterData.put('→',   0x810a8e08L);
		characterData.put('\ufffd',    0x7ffb57dffL);
	}
}
