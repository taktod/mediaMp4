package com.ttProject.media.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.media.mp4.ParentAtom;
import com.ttProject.media.mp4.atom.Co64;
import com.ttProject.media.mp4.atom.Dinf;
import com.ttProject.media.mp4.atom.Iods;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.media.mp4.atom.Stco;
import com.ttProject.media.mp4.atom.Stsc;
import com.ttProject.media.mp4.atom.Stsz;
import com.ttProject.media.mp4.atom.Tkhd;
import com.ttProject.media.mp4.atom.Trak;
import com.ttProject.media.mp4.atom.Udta;
import com.ttProject.media.version4.AtomAnalyzer;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IReadChannel;
import com.ttProject.util.BufferUtil;

@SuppressWarnings("unused")
public class Version4Test {
	ExecutorService es = Executors.newCachedThreadPool();
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
//	@Test
	public void tttest() throws Exception {
		System.out.println(System.getProperty("java.io.tmpdir"));
//		System.out.println("start");
//		new ContentsManager4("http://49.212.39.17/mario.mp4");
//		System.out.println("end");
	}
	public void ttest() throws Exception {
		es.execute(new Runnable() {
			public void run() {
				try {
					Thread.sleep(20000);
					System.out.println("おわった。");
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		try {
			es.shutdown();
			es.awaitTermination(10, TimeUnit.SECONDS);
			es.shutdownNow(); // 処理があったら殺す必要あり。
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("end");
	}
	@Test
	public void test() throws Exception {
		// 前の出力があったら邪魔なので削除する。
		new File(output).delete();
		// 読み込みターゲットを開く
		IReadChannel fc = FileReadChannel.openFileReadChannel(target);

		// 解析処理
		IAtomAnalyzer analyzer = new AtomAnalyzer();
		Atom atom = null;
		Moov moov = null;
		while((atom = analyzer.analyze(fc)) != null) {
			if(atom instanceof Moov) {
				moov = (Moov)atom; // moovの部分だけ知りたい
			}
		}
		// サイズを確認してファイルをつくっていきます。
		List<Atom> list = new ArrayList<Atom>();
		int size = atoms(moov, list, 8);
		if(stsz == null || stsc == null || (stco == null && co64 == null)) {
			throw new Exception("stsz, stsc, stco(co64)がないので、mp4として不正です。");
		}
		// あとは本体のコピーを実施する必要あり。
		try {
			// 新しいファイルをつくりはじめる。
			FileChannel target = new FileOutputStream(output, true).getChannel();
			int chunkTotal = makeHdr(fc, list, size, target);
			// とりあえずここからは、mdatの生成が必要。
			makeBody(fc, target, chunkTotal);
			target.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}// */
		fc.close();
		System.out.println("おわり");
	}
	private void makeBody(IReadChannel fc, FileChannel target,
			int chunkTotal) throws IOException, Exception {
		ByteBuffer buffer;
		// 読み込みデータ量をしっておく。
		buffer = ByteBuffer.allocate(8);
		buffer.putInt(chunkTotal);
		buffer.put("mdat".getBytes());
		buffer.flip();
		target.write(buffer);

		// mdatの内容を書いておきたい。
		IReadChannel index = FileReadChannel.openFileReadChannel(tmp);
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
	}
	private int makeHdr(IReadChannel fc, List<Atom> list, int size,
			FileChannel target) throws IOException,
			Exception {
		FileChannel idx = new FileOutputStream(tmp).getChannel();
		int pos;
		ByteBuffer buffer;
		// ftyp
		buffer = ByteBuffer.allocate(Version4Test.ftyp.length + 8);
		buffer.put(Version4Test.ftyp);
		// moov
		buffer.putInt(size);
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
				fc.position(aa.getPosition());
				BufferUtil.quickCopy(fc, target, aa.getSize());
			}
		}
		// stcoから16バイトコピーしておく。
		if(co64 == null) {
			fc.position(stco.getPosition());
		}
		else {
			fc.position(co64.getPosition());
		}
		buffer = BufferUtil.safeRead(fc, 12);
		target.write(buffer);
		buffer = BufferUtil.safeRead(fc, 4);
		target.write(buffer);
		buffer.position(0);
		idx.write(buffer);
		buffer.position(0);
		idx.write(buffer);

/*		buffer = BufferUtil.safeRead(fc, 16);
		target.write(buffer);
		buffer.position(12);
		int stcoCount = buffer.getInt();
		buffer = ByteBuffer.allocate(8);
		buffer.putInt(0);
		buffer.putInt(stcoCount);
		buffer.flip();
		idx.write(buffer);*/

		// この動作はちょっと危険
		pos = size + ftyp.length + 8; // データの開始位置をいれておく。
		IReadChannel stscReader = FileReadChannel.openFileReadChannel(this.target);
		IReadChannel stszReader = FileReadChannel.openFileReadChannel(this.target);
		stscReader.position(stsc.getPosition() + 12);
		int stscCount = BufferUtil.safeRead(stscReader, 4).getInt();
		stszReader.position(stsz.getPosition() + 12);
		// stszの値はとりあえず無視しておく。(ただしいと思っておく)
		int stszConstant = BufferUtil.safeRead(stszReader, 4).getInt();
		int stszCount = BufferUtil.safeRead(stszReader, 4).getInt();
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
					if(stszConstant == 0) {
						chunkSize += BufferUtil.safeRead(stszReader, 4).getInt();
					}
					else {
						chunkSize += stszConstant;
					}
				}
				// 位置情報を書き込みます
				// co64の場合は8バイトになる
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
			if(stszConstant == 0) {
				chunkSize += BufferUtil.safeRead(stszReader, 4).getInt();
			}
			else {
				chunkSize += stszConstant;
			}
		}
		// co64の場合は8バイトになるんだが・・・
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
		idx.close();
		return chunkTotal;
	}
	// atomをソートして必要なもののみでてくるようにする。
	private int atoms(ParentAtom parent, List<Atom> list, int pos) {
		ParentAtom next = null;
		for(Atom atom : parent.getAtoms()) {
			if(atom instanceof Trak) {
				Trak t = (Trak)atom;
				// Tkhdをもっているか確認。
				boolean findTkhd = false;
				for(Atom a : t.getAtoms()) {
					if(a instanceof Tkhd) {
						findTkhd = true;
						break;
					}
				}
				if(!findTkhd) {
					continue;
				}
				next = t;
				continue;
			}
			else if(atom instanceof Iods || atom instanceof Udta) {
				continue;
			}
			else if(atom instanceof Stsc) {
				stsc = (Stsc)atom;
			}
			else if(atom instanceof Stsz) {
				stsz = (Stsz)atom;
			}
			else if(atom instanceof Stco) {
				stco = (Stco)atom;
				pos += atom.getSize();
				continue;
			}
			else if(atom instanceof Co64) {
				co64 = (Co64)atom;
				pos += atom.getSize();
				continue;
			}
			else if(atom instanceof ParentAtom) {
				next = (ParentAtom)atom;
				continue;
			}
			pos += atom.getSize();
			list.add(atom);
		}
		if(next != null) {
			list.add(next);
			pos += 8;
			pos = atoms(next, list, pos);
		}
		return pos;
	}
}
