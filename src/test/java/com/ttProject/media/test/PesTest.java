package com.ttProject.media.test;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.ttProject.media.aac.Frame;
import com.ttProject.media.aac.FrameAnalyzer;
import com.ttProject.media.aac.IFrameAnalyzer;
import com.ttProject.media.aac.frame.Aac;
import com.ttProject.media.mpegts.CodecType;
import com.ttProject.media.mpegts.field.PmtElementaryField;
import com.ttProject.media.mpegts.packet.Pat;
import com.ttProject.media.mpegts.packet.Pes;
import com.ttProject.media.mpegts.packet.Pmt;
import com.ttProject.media.mpegts.packet.Sdt;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IReadChannel;

/**
 * mpegtsのpesを構築するテスト
 * 他のメディアデータを扱う必要がありそうなので、連携がとれるmediaMp4にかいておきます。
 * @author taktod
 *
 */
public class PesTest {
//	@Test
	public void test() throws Exception {
		// 書き込み対象
		FileOutputStream output = null;
		// 読み込み対象
		IReadChannel target = null;
		try {
			// 書き込み対象
			output = new FileOutputStream("output2.ts");
			// 読み込み対象とりあえずadtsのaacにしておく。
			target = FileReadChannel.openFileReadChannel(
					Thread.currentThread().getContextClassLoader().getResource("smile.aac")
			);
			// とりあえずファイルにして再生できるかで判定したいので、つくってみることにする。
			// sdt
			Sdt sdt = new Sdt();
			sdt.writeDefaultProvider("taktodTools", "mpegtsMuxer");
			output.getChannel().write(sdt.getBuffer());

			// pat
			Pat pat = new Pat();
			output.getChannel().write(pat.getBuffer());
			
			// pmt
			Pmt pmt = new Pmt();
			pmt.addNewField(PmtElementaryField.makeNewField(CodecType.AUDIO_AAC));
			output.getChannel().write(pmt.getBuffer());

			// ここからpesをつくっていく
			int totalFrame = 0;
			int j = 0;
			while(true) {
				// 本当は適当なタイミングでpatとpmtを再度代入すべき
				j ++;
				// pesをつくる。
				// これからpesに含めるデータを取り出す(複数のpesに渡るデータで問題ない)
				// aacの解析
				List<Frame> frames = new ArrayList<Frame>();
				IFrameAnalyzer analyzer = new FrameAnalyzer();
				// 読み込むべきデータサイズ
				int dataSize = 0;
				// 内包するフレーム数
				int findFrame = 0;
				// 処理フレーム
				Frame frame = null;
				float samplingRate = -1;
				while((frame = analyzer.analyze(target)) != null) {
					// みつけたフレーム
					findFrame ++;
					totalFrame ++;
					// フレーム保持
					frames.add(frame);
					if(samplingRate == -1 && frame instanceof Aac) {
						samplingRate = ((Aac)frame).getSamplingRate();
					}
					// データ量を計算しておく
					dataSize += frame.getSize();
					// 現在のカウンター / 44.1fが再生時刻(1秒ごとに切っておく)
					if(samplingRate != -1 && 1.024 * totalFrame / samplingRate > 1 * j) {
						break;
					}
				}
				// 今回みつけた追記フレームがない場合はもうデータがない
				if(findFrame == 0) {
					System.out.println("データがなくなった");
					// もうデータがない
					break;
				}
				// フレームデータをbuffer化する。
				ByteBuffer buffer = ByteBuffer.allocate(dataSize);
				for(Frame f : frames) {
					buffer.put(f.getBuffer());
				}
				buffer.flip();
				// aacは1フレームあたり1024サンプルあるので、1.024でかけないとだめ
				// pesを作成(とりあえずptsのみ指定でいく)
				Pes pes = new Pes(CodecType.AUDIO_AAC, true, (short)0x0100, buffer, (long)(90000L * 1.024 * totalFrame / samplingRate));
				// adaptationFieldにデータをいれる必要があるので、追記しておく。
				pes.getAdaptationField().setPcrBase((long)(90000L * 1.024 * totalFrame / samplingRate));
				// データを取り出す(データがなくなるまでgetBufferで188バイトずつ取得できるようになっている。)
				ByteBuffer buf = null;
				while((buf = pes.getBuffer()) != null) {
					// 取得データを書き込んでいく
					output.getChannel().write(buf);
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if(output != null) {
				try {
					output.close();
				}
				catch (Exception e) {
				}
				output = null;
			}
			if(target != null) {
				try {
					target.close();
				}
				catch (Exception e) {
				}
				target = null;
			}
		}
	}
}
