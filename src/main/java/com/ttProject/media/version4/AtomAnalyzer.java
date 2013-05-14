package com.ttProject.media.version4;

import java.nio.ByteBuffer;

import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.media.mp4.ParentAtom;
import com.ttProject.media.mp4.Type;
import com.ttProject.media.mp4.atom.Iods;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.media.mp4.atom.Stco;
import com.ttProject.media.mp4.atom.Stsc;
import com.ttProject.media.mp4.atom.Stsz;
import com.ttProject.media.mp4.atom.Tkhd;
import com.ttProject.media.mp4.atom.Trak;
import com.ttProject.media.mp4.atom.Udta;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.util.BufferUtil;

/**
 * atomの中身を解析します。
 * @author taktod
 */
public class AtomAnalyzer implements IAtomAnalyzer {
	public Atom analyze(IFileReadChannel ch) throws Exception {
		// 最後まで解析が済んだ場合はおわっておく。
		if(ch.size() == ch.position()) {
			return null;
		}
		int position = ch.position();
		ByteBuffer buffer = BufferUtil.safeRead(ch, 8);
		int size = buffer.getInt();
		String tag = BufferUtil.getDwordText(buffer);
		Type type = Type.getType(tag);
		switch(type) {
		case Moov:
			Moov moov = new Moov(size, position);
			moov.analyze(ch, this);
			ch.position(position + size);
			return moov;
		case Iods: // 消す候補
			// iodsは必要ないと思う。消す。
			Iods iods = new Iods(size, position);
			ch.position(position + size);
			return iods;
		case Udta: // 消す候補
			// udtaはいらない。
			Udta udta = new Udta(size, position);
			ch.position(position + size);
			return udta;
		case Trak:
			Trak trak = new Trak(size, position);
			trak.analyze(ch, this);
			ch.position(position + size);
			return trak;
		case Tkhd:
			Tkhd tkhd = new Tkhd(size, position);
			tkhd.analyze(ch);
			ch.position(position + size);
			if(tkhd.getWidth() != 0 || tkhd.getHeight() != 0) {
				return null;
			}
			else {
				return tkhd;
			}
		case Mdia:
		case Minf:
		case Stbl:
			ParentAtom parentAtom = new ParentAtom(tag, size, position) {
			};
			parentAtom.analyze(ch, this);
			ch.position(position + size);
			return parentAtom;
		case Stsc:
			// 各チャンクのサンプル量
			Stsc stsc = new Stsc(size, position);
			ch.position(position + size);
			return stsc;
		case Stsz:
			// サンプルのサイズ量
			Stsz stsz = new Stsz(size, position);
			ch.position(position + size);
			return stsz;
		case Stco:
			// 各チャンクの開始位置
			Stco stco = new Stco(size, position);
			ch.position(position + size);
			return stco;
		}
		ch.position(position + size);
		return new Atom(tag, size, position) {
			@Override
			public void analyze(IFileReadChannel ch, IAtomAnalyzer analyzer)
					throws Exception {
				;
			}
		};
	}
}
