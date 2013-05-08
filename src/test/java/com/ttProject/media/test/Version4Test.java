package com.ttProject.media.test;

import java.nio.ByteBuffer;

import org.junit.Test;

import com.ttProject.library.BufferUtil;
import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.media.mp4.atom.Dinf;
import com.ttProject.media.mp4.atom.Ftyp;
import com.ttProject.media.mp4.atom.Hdlr;
import com.ttProject.media.mp4.atom.Iods;
import com.ttProject.media.mp4.atom.Mdat;
import com.ttProject.media.mp4.atom.Mdhd;
import com.ttProject.media.mp4.atom.Mdia;
import com.ttProject.media.mp4.atom.Minf;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.media.mp4.atom.Mvhd;
import com.ttProject.media.mp4.atom.Smhd;
import com.ttProject.media.mp4.atom.Stbl;
import com.ttProject.media.mp4.atom.Stco;
import com.ttProject.media.mp4.atom.Stsc;
import com.ttProject.media.mp4.atom.Stsd;
import com.ttProject.media.mp4.atom.Stss;
import com.ttProject.media.mp4.atom.Stsz;
import com.ttProject.media.mp4.atom.Stts;
import com.ttProject.media.mp4.atom.Tkhd;
import com.ttProject.media.mp4.atom.Trak;
import com.ttProject.media.mp4.atom.Udta;
import com.ttProject.media.mp4.atom.Vmhd;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IFileReadChannel;

public class Version4Test {
	/** 替わりに設定するftyp値 */
	private static byte[] ftyp = {
		0x00, 0x00, 0x00, 0x1C,
		'f', 't', 'y', 'p',
		'M', '4', 'A', ' ',
		0x00, 0x00, 0x00, 0x00,
		'i', 's', 'o', 'm',
		'M', '4', 'A', ' ',
		'm', 'p', '4', '2'
	};
	@Test
	public void test() throws Exception {
		// ここで動作を実施します。
		// とりあえずthreadの中身が実行することをここでやっとく。
		IFileReadChannel fc = FileReadChannel.openFileReadChannel("http://49.212.39.17/mario.mp4");
		// 解析動作
		IAtomAnalyzer analyzer = new IAtomAnalyzer() {
			@Override
			public Atom analyze(IFileReadChannel ch) throws Exception {
				if(ch.size() == ch.position()) {
					// もうデータがない
					return null;
				}
				int position = ch.position();
				ByteBuffer buffer = BufferUtil.safeRead(ch, 8);
				int size = buffer.getInt();
				byte[] name = new byte[4];
				buffer.get(name);
				String tag = new String(name).toLowerCase();
				// データを読み取って解析をすすめる。
				if("ftyp".equals(tag)) {
					// ここで解析するべきはオリジナルのデータ
					Ftyp ftyp = new Ftyp(size, position);
					ftyp.analyze(ch);
					ch.position(position + size);
					return ftyp;
				}
				if("moov".equals(tag)) {
					// moovの中身は別途解析する必要あり。
					Moov moov = new Moov(size, position);
					moov.analyze(ch, this);
					ch.position(position + size);
					return moov;
				}
				if("mvhd".equals(tag)) {
					Mvhd mvhd = new Mvhd(size, position);
					// とりあえず解析せずほっとく
					ch.position(position + size);
					return mvhd;
				}
				if("iods".equals(tag)) {
					// iodsは必要ないと思う。消す。
					Iods iods = new Iods(size, position);
					ch.position(position + size);
					return iods;
				}
				if("trak".equals(tag)) {
					Trak trak = new Trak(size, position);
					trak.analyze(ch, this);
					ch.position(position + size);
					return trak;
				}
				if("udta".equals(tag)) {
					// udtaはいらない。
					Udta udta = new Udta(size, position);
					ch.position(position + size);
					return udta;
				}
				if("tkhd".equals(tag)) {
					Tkhd tkhd = new Tkhd(size, position);
					tkhd.analyze(ch);
					ch.position(position + size);
					if(tkhd.getWidth() != 0 || tkhd.getHeight() != 0) {
						return null;
					}
					else {
						return tkhd;
					}
				}
				if("mdia".equals(tag)) {
					Mdia mdia = new Mdia(size, position);
					mdia.analyze(ch, this);
					ch.position(position + size);
					return mdia;
				}
				if("mdhd".equals(tag)) {
					Mdhd mdhd = new Mdhd(size, position);
					ch.position(position + size);
					return mdhd;
				}
				if("hdlr".equals(tag)) {
					Hdlr hdlr = new Hdlr(size, position);
					ch.position(position + size);
					return hdlr;
				}
				if("minf".equals(tag)) {
					Minf minf = new Minf(size, position);
					minf.analyze(ch, this);
					ch.position(position + size);
					return minf;
				}
				if("vmhd".equals(tag)) {
					Vmhd vmhd = new Vmhd(size, position);
					ch.position(position + size);
					return vmhd;
				}
				if("smhd".equals(tag)) {
					Smhd smhd = new Smhd(size, position);
					ch.position(position + size);
					return smhd;
				}
				if("dinf".equals(tag)) {
					Dinf dinf = new Dinf(size, position);
					ch.position(position + size);
					return dinf;
				}
				if("stbl".equals(tag)) {
					Stbl stbl = new Stbl(size, position);
					stbl.analyze(ch, this);
					ch.position(position + size);
					return stbl;
				}
				if("stsd".equals(tag)) {
					Stsd stsd = new Stsd(size, position);
					ch.position(position + size);
					return stsd;
				}
				if("stts".equals(tag)) {
					Stts stts = new Stts(size, position);
					ch.position(position + size);
					return stts;
				}
				if("stss".equals(tag)) {
					// 映像の場合はkeyFrame指示になるっぽい
					Stss stss = new Stss(size, position);
					ch.position(position + size);
					return stss;
				}
				if("stsc".equals(tag)) {
					// 各チャンクのサンプル量
					Stsc stsc = new Stsc(size, position);
					ch.position(position + size);
					return stsc;
				}
				if("stsz".equals(tag)) {
					// サンプルのサイズ量
					Stsz stsz = new Stsz(size, position);
					ch.position(position + size);
					return stsz;
				}
				if("stco".equals(tag)) {
					// 各チャンクの開始位置
					Stco stco = new Stco(size, position);
					ch.position(position + size);
					return stco;
				}
				if("mdat".equals(tag)) {
					Mdat mdat = new Mdat(size, position);
//					ch.position(position + size);
					return null;
				}
				System.out.println(tag);
				return null;
			}
		};
		Atom atom = null;
		Ftyp ftyp = null;
		Moov moov = null;
		Mdat mdat = null;
		while((atom = analyzer.analyze(fc)) != null) {
			if(atom instanceof Ftyp) {
				ftyp = (Ftyp)atom;
			}
			if(atom instanceof Moov) {
				moov = (Moov)atom;
			}
			System.out.println(atom);
		}
		fc.close();
		System.out.println("ftyp:1C");
		System.out.println("moov:8"); // header部
		for(Atom at : moov.getAtoms()) {
			if(at instanceof Mvhd) {
				System.out.println("  mvhd:" + Integer.toHexString(at.getSize()));
				continue;
			}
			if(at instanceof Trak) {
				Trak t = (Trak)at;
				Tkhd tkhd = null;
				for(Atom att : t.getAtoms()) {
					if(att instanceof Tkhd) {
						tkhd = (Tkhd)att;
					}
				}
				if(tkhd == null) {
					// 動画データなのでパスする。
					continue;
				}
				System.out.println("  trak:" + Integer.toHexString(at.getSize()));
			}
//			System.out.println(at);
		}
		// ここまででmoovのサイズが決定します。
	}
}
