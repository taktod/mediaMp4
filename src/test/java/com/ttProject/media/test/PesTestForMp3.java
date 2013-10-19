package com.ttProject.media.test;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.ttProject.media.mp3.Frame;
import com.ttProject.media.mp3.FrameAnalyzer;
import com.ttProject.media.mp3.IFrameAnalyzer;
import com.ttProject.media.mp3.frame.Mp3;
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
 * mp3でもやってみようと思う。
 * @author taktod
 */
public class PesTestForMp3 {
	@Test
	public void test() {
		// 書き込み対象
		FileOutputStream output = null;
		// 読み込み対象
		IReadChannel target = null;
		try {
			output = new FileOutputStream("output3.ts");
			target = FileReadChannel.openFileReadChannel(
					Thread.currentThread().getContextClassLoader().getResource("smile.mp3")
			);
			// sdt
			Sdt sdt = new Sdt();
			sdt.writeDefaultProvider("taktodTools", "mpegtsMuxer");
			output.getChannel().write(sdt.getBuffer());

			// pat
			Pat pat = new Pat();
			output.getChannel().write(pat.getBuffer());
			
			// pmt
			Pmt pmt = new Pmt();
			pmt.addNewField(PmtElementaryField.makeNewField(CodecType.AUDIO_MPEG1));
			output.getChannel().write(pmt.getBuffer());
			// ここからpesを作成する。
			int totalFrame = 0;
			int j = 0;
			while(true) {
				j ++;
				List<Frame> frames = new ArrayList<Frame>();
				IFrameAnalyzer analyzer = new FrameAnalyzer();
				int dataSize = 0;
				int findFrame = 0;
				Frame frame = null;
				float samplingRate = -1;
				while((frame = analyzer.analyze(target)) != null) {
					if(!(frame instanceof Mp3)) {
						continue;
					}
					findFrame ++;
					totalFrame ++;
					frames.add(frame);
					if(samplingRate == -1) {
						samplingRate = ((Mp3)frame).getSamplingRate();
					}
					dataSize +=  frame.getSize();
					if(samplingRate != -1 && 1.152 * totalFrame / samplingRate > 1 * j) {
						break;
					}
				}
				if(findFrame == 0) {
					System.out.println("データがもうない。");
					break;
				}
				ByteBuffer buffer = ByteBuffer.allocate(dataSize);
				for(Frame f : frames) {
					buffer.put(f.getBuffer());
				}
				buffer.flip();
				// mp3は1フレームあたり1152サンプルあるっぽい。(versionとlayerに依存するみたいなので、注意が必要あと、サンプル数が違うデータがも混入する可能性があるので、この計算方法は本当はまちがってるはず)
				// 本来なら、frame数から割り出すのではなく、先頭のフレームからサンプル数をきちんと数えるべき。
				Pes pes = new Pes(CodecType.AUDIO_MPEG1, true, (short)0x0100, buffer, (long)(90000L * 1.152 * totalFrame / samplingRate));
				pes.getAdaptationField().setPcrBase((long)(90000L * 1.152 * totalFrame / samplingRate));
				ByteBuffer buf = null;
				while((buf = pes.getBuffer()) != null) {
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
			}
			if(target != null) {
				try {
					target.close();
				}
				catch (Exception e) {
				}
			}
		}
	}
}
