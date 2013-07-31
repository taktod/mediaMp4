package com.ttProject.media.test;

import java.awt.BufferCapabilities.FlipContents;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.ttProject.media.aac.DecoderSpecificInfo;
import com.ttProject.media.aac.frame.Aac;
import com.ttProject.media.extra.flv.FlvOrderModel;
import com.ttProject.media.extra.mp4.IndexFileCreator;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.media.h264.ConfigData;
import com.ttProject.media.h264.DataNalAnalyzer;
import com.ttProject.media.h264.Frame;
import com.ttProject.media.h264.IFrameAnalyzer;
import com.ttProject.media.h264.frame.AccessUnitDelimiter;
import com.ttProject.media.h264.frame.PictureParameterSet;
import com.ttProject.media.h264.frame.SequenceParameterSet;
import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.media.mpegts.CodecType;
import com.ttProject.media.mpegts.field.AdaptationField;
import com.ttProject.media.mpegts.field.PmtElementaryField;
import com.ttProject.media.mpegts.packet.Pat;
import com.ttProject.media.mpegts.packet.Pes;
import com.ttProject.media.mpegts.packet.Pmt;
import com.ttProject.media.mpegts.packet.Sdt;
import com.ttProject.nio.channels.ByteReadChannel;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IReadChannel;

/**
 * version6 mpegts変換のテスト
 * @author taktod
 * 
 * version5と同じ情報があれば、作成可能だと思われます。
 * とりあえず、aacのみのmpegtsをつくるところからせめて見たい。
 */
public class Version6Test {
	@Test
	public void analyzeTest() throws Exception {
		SequenceParameterSet sps = null;
		PictureParameterSet pps = null;
		int duration = 10000; // 10秒ごとに切除することにする。
		IReadChannel fc = FileReadChannel.openFileReadChannel("http://49.212.39.17/mario.mp4");
		IndexFileCreator analyzer = new IndexFileCreator(new File("output2.tmp"));
		Atom atom = null;
		while((atom = analyzer.analyze(fc)) != null) {
			if(atom instanceof Moov) {
				break;
			}
		}
		analyzer.updatePrevTag();
		analyzer.checkDataSize();
		analyzer.close();
		// 解析用のindexファイル作成おわり。
		IReadChannel tmp = FileReadChannel.openFileReadChannel(new File("output2.tmp").getAbsolutePath());
		FlvOrderModel orderModel = new FlvOrderModel(tmp, true, true, 0); // はじめからでとりあえずOK
		// 開始位置については知る必要なし。
		FileChannel output = new FileOutputStream("target.ts").getChannel();
		// ここからが本番になるところ。
		// まずは、mpegtsの全体のデータをつくっていくことにする。
		// とりあえず映像だけ抜き出してやってみる。
		// sdt
		Sdt sdt = new Sdt();
		sdt.writeDefaultProvider("taktodTools", "mpegtsMuxer");
		output.write(sdt.getBuffer());
		// pat
		Pat pat = new Pat();
		output.write(pat.getBuffer());
		// pmt
		Pmt pmt = new Pmt();
		pmt.addNewField(PmtElementaryField.makeNewField(CodecType.VIDEO_H264));
		pmt.addNewField(PmtElementaryField.makeNewField(CodecType.AUDIO_AAC));
		output.write(pmt.getBuffer());

		// データ
		// とすればよさそう。// このままだとindexの番号がわからないっぽいが・・・どうするかなぁ・・・
		// とりあえずh264の10秒動画をつくろう。
		IFrameAnalyzer frameAnalyzer = new DataNalAnalyzer();
		// audioデータは固めておいて、pcrの書き込みの前に一気に書き込む
		List<Aac> aacList = new ArrayList<Aac>();
		int startAacTimestamp = -1;
		DecoderSpecificInfo dsi = null;
		List<Tag> tagList;
		while((tagList = orderModel.nextTagList(fc)) != null) {
			// tagデータにデータがはいっています。
			for(Tag tag : tagList) {
//				System.out.println(tag);
				if(tag instanceof AudioTag) { // audioTagの内容は保持しておく。
					AudioTag audioTag = (AudioTag) tag;
					if(audioTag.isMediaSequenceHeader()) {
						dsi = new DecoderSpecificInfo(new ByteReadChannel(audioTag.getRawData()));
					}
					else {
						if(startAacTimestamp == -1) {
							startAacTimestamp = audioTag.getTimestamp();
						}
						Aac aac = new Aac(0, dsi);
						aac.setData(audioTag.getRawData());
						aacList.add(aac);
					}
				}
				else if(tag instanceof VideoTag) {
					VideoTag videoTag = (VideoTag) tag;
					IReadChannel dataChannel = new ByteReadChannel(videoTag.getRawData());
					dataChannel.position(3);
					if(videoTag.isMediaSequenceHeader()) {
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
							throw new Exception("spsかppsが未指定です。");
						}
						AccessUnitDelimiter aud = new AccessUnitDelimiter();
						Frame frame = frameAnalyzer.analyze(dataChannel);
						ByteBuffer writeData;
						if(videoTag.isKeyFrame()) {
							if(aacList.size() != 0) {
								System.out.println("aacを追記します。");
								// aacの全データの量を調べます。
								// 書き込むサイズを知っておく。
								int size = 0;
								for(Aac aac : aacList) {
									size += aac.getBuffer().remaining();
								}
								System.out.println("追記サイズ:" + size);
								writeData = ByteBuffer.allocate(size);
								for(Aac aac : aacList) {
									writeData.put(aac.getBuffer());
								}
								writeData.flip();
								System.out.println("実データサイズ:" + writeData.remaining());
								Pes pes = new Pes(CodecType.AUDIO_AAC, false, (short)0x0101, writeData, (90L * startAacTimestamp));
								pes.setAdaptationFieldExist(1);
								AdaptationField adaptationField = new AdaptationField();
								adaptationField.setRandomAccessIndicator(1);
								adaptationField.setLength(1);
								pes.setAdaptationField(adaptationField);
								ByteBuffer buf = null;
								while((buf = pes.getBuffer()) != null) {
									output.write(buf);
								}
								// おわったらクリアしておく。
								startAacTimestamp = -1;
								aacList.clear();
							}
							// 先にaacのデータがあったら、のこっている分すべて書き込んでおきます。
							writeData = ByteBuffer.allocate(
									4 + aud.getSize() +
									4 + sps.getSize() +
									4 + pps.getSize() +
									4 + frame.getSize()
							);
							writeData.putInt(1);
							writeData.put(aud.getData());
							writeData.putInt(1);
							writeData.put(sps.getData());
							writeData.putInt(1);
							writeData.put(pps.getData());
							writeData.putInt(1);
							writeData.put(frame.getData());
							writeData.flip();
							Pes pes = new Pes(CodecType.VIDEO_H264, true, (short)0x0100, writeData, (long)(90L * videoTag.getTimestamp()));
							pes.getAdaptationField().setPcrBase((long)(90L * videoTag.getTimestamp()));
							ByteBuffer buf = null;
							while((buf = pes.getBuffer()) != null) {
								output.write(buf);
							}
						}
						else {
							writeData = ByteBuffer.allocate(
									4 + aud.getSize() +
									4 + frame.getSize()
							);
							writeData.putInt(1);
							writeData.put(aud.getData());
							writeData.putInt(1);
							writeData.put(frame.getData());
							writeData.flip();
							Pes pes = new Pes(CodecType.VIDEO_H264, false, (short)0x0100, writeData, (long)(90L * videoTag.getTimestamp()));
							ByteBuffer buf = null;
							while((buf = pes.getBuffer()) != null) {
								output.write(buf);
							}
						}
					}
					dataChannel.close();
				}
			}
		}
		fc.close();
		tmp.close();
		output.close();
	}
}
