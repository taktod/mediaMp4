package com.ttProject.media.test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.ttProject.library.BufferUtil;
import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.media.mp4.ParentAtom;
import com.ttProject.media.mp4.atom.Co64;
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
	private String target = "http://49.212.39.17/mario.mp4";
	private String output = "output.mp4";
	private String tmp = "output.idx"; // 新旧長さ
	private Stsc stsc = null;
	private Stsz stsz = null;
	private Stco stco = null;
	private Co64 co64 = null;
	@Test
	public void test() throws Exception {
		// 前の出力があったら邪魔なので削除する。
		new File(output).delete();
		// 読み込みターゲットを開く
		IFileReadChannel fc = FileReadChannel.openFileReadChannel(target);

		// 解析処理
		IAtomAnalyzer analyzer = new IAtomAnalyzer() {
			@Override
			public Atom analyze(IFileReadChannel ch) throws Exception {
				if(ch.size() == ch.position()) {
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
					// mdatの位置を考えることでmoovが後ろにあるmp4でも対応できるようになる。(ただし処理がおそくなる)
					ch.position(position + size);
					return mdat;
				}
				// TODO freeという余計なタグがある可能性あり。
				System.out.println(tag);
				return null;
			}
		};
		Atom atom = null;
		Moov moov = null;
		while((atom = analyzer.analyze(fc)) != null) {
			if(atom instanceof Moov) {
				moov = (Moov)atom; // moovの部分だけ知りたい
			}
		}
		// ここまでatomの準備できあがり
		// サイズを確認してファイルをつくっていきます。
		List<Atom> list = new ArrayList<Atom>();
		int pos = moovAtoms(moov, list, Version4Test.ftyp.length + 8);
		// このタイミングですでにstscとstszのコピーははじまっている。
		// あとは本体のコピーを実施する必要あり。
		try {
			// 新しいファイルをつくりはじめる。
			FileChannel target = new FileOutputStream(output, true).getChannel();
			ByteBuffer buffer = null;
			// ftyp
			buffer = ByteBuffer.allocate(Version4Test.ftyp.length + 8);
			buffer.put(Version4Test.ftyp);
			// moov
			buffer.putInt(pos - Version4Test.ftyp.length);
			buffer.put("moov".getBytes());
			buffer.flip();
			target.write(buffer);
			// 通常のタグを書き込む
			for(Atom aa : list) {
				if(aa instanceof ParentAtom && !(aa instanceof Dinf)) {
					buffer = ByteBuffer.allocate(8);
					buffer.putInt(aa.getSize());
					buffer.put(aa.getName().getBytes());
					buffer.flip();
					target.write(buffer);
				}
				else {
					aa.copy(fc, target);
				}
			}
			// stcoを構成したい。
			FileChannel idx = new FileOutputStream(tmp).getChannel();
			if(stco != null) {
				// stcoから16バイトコピーしておく。
				fc.position(stco.getPosition());
				buffer = BufferUtil.safeRead(fc, 16);
				target.write(buffer);
				buffer.position(12);
				int stcoCount = buffer.getInt();
				buffer = ByteBuffer.allocate(8);
				buffer.putInt(0);
				buffer.putInt(stcoCount);
				buffer.flip();
				idx.write(buffer);
//				target.write(BufferUtil.safeRead(fc, 16));
				pos += 8; // データの開始位置をいれておく。
			}
			else {
				throw new RuntimeException("stcoがない");
			}
			IFileReadChannel stscReader = FileReadChannel.openFileReadChannel(this.target);
			IFileReadChannel stszReader = FileReadChannel.openFileReadChannel(this.target);
			stscReader.position(stsc.getPosition() + 12);
			int stscCount = BufferUtil.safeRead(stscReader, 4).getInt();
			stszReader.position(stsz.getPosition() + 12);
			// stszの値はとりあえず無視しておく。(ただしいと思っておく)
			int stszConstant = BufferUtil.safeRead(stszReader, 4).getInt();
			int stszCount = BufferUtil.safeRead(stszReader, 4).getInt();
//			System.out.println(Integer.toHexString(stscCount));
//			System.out.println(Integer.toHexString(stszConstant));
//			System.out.println(Integer.toHexString(stszCount));
			// stcoの内容を構築していきます。(co64の場合もあり)
			// 元のデータがどこからか？という情報も必要(アクセス用)
			int chunkTotal = 0;
			int currentChunk = 1;
			int currentSampleCount = 0;
			for(int i = 0;i < stscCount;i ++) {
				// stscのあるかぎり読み込む
				buffer = BufferUtil.safeRead(stscReader, 12);
				int chunk = buffer.getInt();
				for(;currentChunk < chunk;currentChunk ++) {
					// 各チャンク用のsampleの大きさを計算する必要あり。
					int chunkSize = 0;
					for(int j = 0;j < currentSampleCount;j ++) {
						chunkSize += BufferUtil.safeRead(stszReader, 4).getInt();
					}
					// 位置情報を書き込みます
					ByteBuffer buf = ByteBuffer.allocate(4);
					buf.putInt(pos);
					buf.flip();
					target.write(buf);
					buf.position(0);
					idx.write(buf);
					idx.write(BufferUtil.safeRead(fc, 4));
					buf = ByteBuffer.allocate(4);
					buf.putInt(chunkSize);
					buf.flip();
					idx.write(buf);
					pos += chunkSize;
					chunkTotal += chunkSize;
				}
				currentSampleCount = buffer.getInt();
				buffer.getInt();
			}
			// 最後まできたら、最後のデータを書き込んでおく
			// 各チャンク用のsampleの大きさを計算する必要あり。
			int chunkSize = 0;
			for(int j = 0;j < currentSampleCount;j ++) {
				chunkSize += BufferUtil.safeRead(stszReader, 4).getInt();
			}
			ByteBuffer buf = ByteBuffer.allocate(4);
			buf.putInt(pos);
			buf.flip();
			target.write(buf);
			buf.position(0);
			idx.write(buf);
			idx.write(BufferUtil.safeRead(fc, 4));
			buf = ByteBuffer.allocate(4);
			buf.putInt(chunkSize);
			buf.flip();
			idx.write(buf);
			pos += chunkSize;
			chunkTotal += chunkSize;

			stscReader.close();
			stszReader.close();
			// とりあえずここからは、mdatの生成が必要。
			// 読み込みデータ量をしっておく。
			buffer = ByteBuffer.allocate(8);
			buffer.putInt(chunkTotal);
			buffer.put("mdat".getBytes());
			buffer.flip();
			target.write(buffer);
			idx.close();

			// mdatの内容を書いておきたい。
			IFileReadChannel index = FileReadChannel.openFileReadChannel(tmp);
			index.position(8);
			while(true) {
				if(index.size() == index.position()) {
					break;
				}
				buffer = BufferUtil.safeRead(index, 12);
				buffer.position(4);
				fc.position(buffer.getInt());
				BufferUtil.quickCopy(fc, target, buffer.getInt());
			}
			index.close();
			target.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		fc.close();
		System.out.println("おわり");
	}
	// atomをソートして必要なもののみでてくるようにする。
	private int moovAtoms(Moov moov, List<Atom> list, int pos) {
		Trak trak = null;
		for(Atom atom : moov.getAtoms()) {
			if(atom instanceof Trak) {
				// trakは次につなげる命令なので最後に足す
				Trak t = (Trak)atom;
				if(t.getAtoms().size() == 0) {
					// 映像のあるatomなので無視
					continue;
				}
				trak = t;
				continue;
			}
			else if(atom instanceof Iods){
				// iodsは拡張命令なので無視する。
				continue;
			}
			else if(atom instanceof Udta){
				// iodsは拡張命令なので無視する。
				continue;
			}
			pos += atom.getSize();
			list.add(atom);
		}
		if(trak != null) {
			list.add(trak);
			pos += 8;
			pos = trakAtoms(trak, list, pos);
		}
		return pos;
	}
	private int trakAtoms(Trak trak, List<Atom> list, int pos) {
		Mdia mdia = null;
		for(Atom atom : trak.getAtoms()) {
			if(atom instanceof Mdia) {
				mdia = (Mdia)atom;
				continue;
			}
			pos += atom.getSize();
			list.add(atom);
		}
		if(mdia != null) {
			list.add(mdia);
			pos += 8;
			pos = mdiaAtoms(mdia, list, pos);
		}
		return pos;
	}
	private int mdiaAtoms(Mdia mdia, List<Atom> list, int pos) {
		Minf minf = null;
		for(Atom atom : mdia.getAtoms()) {
			if(atom instanceof Minf) {
				minf = (Minf)atom;
				continue;
			}
			pos += atom.getSize();
			list.add(atom);
		}
		if(minf != null) {
			list.add(minf);
			pos += 8;
			pos = minfAtoms(minf, list, pos);
		}
		return pos;
	}
	private int minfAtoms(Minf minf, List<Atom> list, int pos) {
		Stbl stbl = null;
		for(Atom atom : minf.getAtoms()) {
			if(atom instanceof Stbl) {
				stbl = (Stbl)atom;
				continue;
			}
			pos += atom.getSize();
			list.add(atom);
		}
		if(stbl != null) {
			list.add(stbl);
			pos += 8;
			pos = stblAtoms(stbl, list, pos);
		}
		return pos;
	}
	private int stblAtoms(Stbl stbl, List<Atom> list, int pos) {
		for(Atom atom : stbl.getAtoms()) {
			if(atom instanceof Stsc) {
				stsc = (Stsc) atom;
			}
			if(atom instanceof Stsz) {
				stsz = (Stsz)atom;
			}
			if(atom instanceof Stco) {
				stco = (Stco)atom;
				continue;
			}
			if(atom instanceof Co64) {
				co64 = (Co64)atom;
				continue;
			}
			pos += atom.getSize();
			list.add(atom);
		}
		if(stco != null) {
			// 情報表示
			pos += stco.getSize();
		}
		if(co64 != null) {
			pos += co64.getSize();
		}
		return pos;
	}
}
