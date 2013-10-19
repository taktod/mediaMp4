package com.ttProject.media.test;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

import org.junit.Test;

import com.ttProject.media.flv.FlvHeader;
import com.ttProject.media.flv.ITagAnalyzer;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.TagAnalyzer;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.media.h264.ConfigData;
import com.ttProject.media.h264.DataNalAnalyzer;
import com.ttProject.media.h264.Frame;
import com.ttProject.media.h264.IFrameAnalyzer;
import com.ttProject.media.h264.frame.AccessUnitDelimiter;
import com.ttProject.media.h264.frame.PictureParameterSet;
import com.ttProject.media.h264.frame.SequenceParameterSet;
import com.ttProject.media.mpegts.CodecType;
import com.ttProject.media.mpegts.field.PmtElementaryField;
import com.ttProject.media.mpegts.packet.Pat;
import com.ttProject.media.mpegts.packet.Pes;
import com.ttProject.media.mpegts.packet.Pmt;
import com.ttProject.media.mpegts.packet.Sdt;
import com.ttProject.nio.channels.ByteReadChannel;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IReadChannel;

/**
 * h264のデータをmpegtsのpesにいれる方法を研究しておく。
 * 
 * とりあえず解析をすすめていく。
 * やったこと
 * marioのデータから、h.264のみのflvとmpegtsを作成。
 * 
 * 各nalデータを抜き出してflvのtimestampとmpegtsのpcr ptsと内包nalデータについて抜き出してみた。
 * 
 * ts:0
 * pcr:5000007B0C7E00 (63000)
 * pts:210007D861 (126000) [63000 / 0.7]
 * 09 aud(きっているデータ)
 * 67 sps
 * 68 pps
 * 65 keyFrame
 * 
 * ts:21 [33]
 * pcr:なし
 * pts:210007EF95 (128970) [65970 / 0.733]
 * 09
 * 41 slice
 * 
 * ts:43 [67]
 * pcr:なし
 * pts:210009077D (132030) [69030 / 0.767]
 * 09
 * 41
 * 
 * ts:64 [100]
 * pcr:なし
 * pts:2100091EB1 (135000) [72000 / 0.8]
 * 09
 * 41
 * 
 * あってるね・・・
 * ts:85
 * pcr:
 * pts:21000935E5
 * 09
 * 41
 * 
 * ....
 * ts:7B1
 * pcr:5000032F197E00 (417330) [4.637]
 * pts:21001DA895 (480330) [417330 / 4.637]
 * 09
 * 67
 * 68
 * 65
 * 
 * どうやらdtsはないっぽい。
 * また、09 41の組み合わせの各innerFrameと09 67 68 65の組み合わせのkeyFrameから成るっぽい。
 * なぜか63000ずれているのは仕様だと思われます。ffmpeg依存？
 * flvの時間と一致しているので、flv→mpegtsの変換ならうまくできそうな感じ。
 * 
 * mpegtsの仕様からするとbframeを含めたh.264の場合はもうちょっとなんとかする必要があるみたいです。
 * とりあえず、そういうサンプルが手元にないのでスルーしておきます。
 * 
 * @author taktod
 */
public class PesTestForH264 {
	@Test
	public void test() throws Exception {
		SequenceParameterSet sps = null;
		PictureParameterSet pps = null;
		FileOutputStream output = null;
		IReadChannel target = null;
		try {
			output = new FileOutputStream("output_1.ts");
			target = FileReadChannel.openFileReadChannel(
					Thread.currentThread().getContextClassLoader().getResource("mario.nosound.flv")
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
			pmt.addNewField(PmtElementaryField.makeNewField(CodecType.VIDEO_H264));
			output.getChannel().write(pmt.getBuffer());
			
			// ここからpesをつくっていく。
			// flvからh.264のデータを取り出して、mpegtsに書き出していく。
			FlvHeader flvHeader = new FlvHeader();
			flvHeader.analyze(target);
			// flvHeader部を読み込みます。
			ITagAnalyzer flvTagAnalyser = new TagAnalyzer();
			IFrameAnalyzer frameAnalyzer = new DataNalAnalyzer();
			try {
				Tag tag = null;
				while((tag = flvTagAnalyser.analyze(target)) != null) {
					// flvのデータが読み込めた場合の処理
					if(!(tag instanceof VideoTag)) {
						// videoデータでなければ、すてておく。
						continue;
					}
					VideoTag videoTag = (VideoTag) tag;
					IReadChannel dataChannel = new ByteReadChannel(videoTag.getRawData());
					dataChannel.position(3);
					try {
						if(videoTag.isMediaSequenceHeader()) {
							// mediaSequenceHeaderの場合はspsとppsを取り出して保持しておく
							ConfigData configData = new ConfigData();
							List<Frame> frames = configData.getNals(dataChannel);
							for(Frame frame : frames) {
								if(frame instanceof SequenceParameterSet) {
									sps = (SequenceParameterSet)frame;
								}
								if(frame instanceof PictureParameterSet) {
									pps = (PictureParameterSet)frame;
								}
							}
						}
						else {
							if(sps == null || pps == null) {
								throw new Exception("spsかppsが未指定になっています。");
							}
							// 通常の動画データの場合は抜き出して処理しておく。
							Frame frame = frameAnalyzer.analyze(dataChannel);
							// 書き込み対象frame
							if(videoTag.isKeyFrame()) {
								// キーフレームの場合
								// unitStartとして書き込み
								// pcrフィールドあり
								System.out.println("キーフレーム書き込みテスト");
								// 書き込みするデータをつくっておく。
								AccessUnitDelimiter aud = new AccessUnitDelimiter();
								ByteBuffer writeData = ByteBuffer.allocate(
										4 + aud.getSize() +
										4 + sps.getSize() +
										4 + pps.getSize() +
										4 + frame.getSize());
								writeData.putInt(1); // nalの記録初め
								writeData.put(aud.getData());
								writeData.putInt(1);
								writeData.put(sps.getData());
								writeData.putInt(1);
								writeData.put(pps.getData());
								writeData.putInt(1);
								writeData.put(frame.getData());
								writeData.flip();
								// pesの作成してデータを書き込みます。
								Pes pes = new Pes(CodecType.VIDEO_H264, true, (short)0x0100, writeData, (long)(90L * videoTag.getTimestamp()));
								pes.getAdaptationField().setPcrBase((long)(90L * videoTag.getTimestamp()));
								ByteBuffer buf = null;
								while((buf = pes.getBuffer()) != null) {
									output.getChannel().write(buf);
								}
							}
							else {
								// innerFrameの場合
								// unitStartとして書き込み
								AccessUnitDelimiter aud = new AccessUnitDelimiter();
								ByteBuffer writeData = ByteBuffer.allocate(
										4 + aud.getSize() +
										4 + frame.getSize()
								);
								writeData.putInt(1);
								writeData.put(aud.getData());
								writeData.putInt(1);
								writeData.put(frame.getData());
								writeData.flip();
								// adaptationFieldはなし。(pcrの記述はなし)
								Pes pes = new Pes(CodecType.VIDEO_H264, false, (short)0x0100, writeData, (long)(90L * videoTag.getTimestamp()));
								ByteBuffer buf = null;
								while((buf = pes.getBuffer()) != null) {
									output.getChannel().write(buf);
								}
							}
						}
					}
					finally {
						dataChannel.close();
					}
				}
			}
			catch (Exception e) {
				e.printStackTrace();
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
