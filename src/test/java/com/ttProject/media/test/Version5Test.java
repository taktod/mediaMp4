package com.ttProject.media.test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.List;

import org.junit.Test;

import com.ttProject.media.flv.Tag;
import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.media.version5.FlvOrderModel;
import com.ttProject.media.version5.IFlvStartEventListener;
import com.ttProject.media.version5.IndexFileCreator;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IFileReadChannel;

/**
 * version5 flv変換のテスト
 * とりあえず変換用の一時データは次のようにします。
 * 
 * 通常のmp4のstsc stsz stco stts stssの情報を保持しておく。
 * keyFrameの部分は別途タグをつくって、保存しておく。
 * カスタムタグとしてvdeo sond metaをつくっておく(videoとsound、metaの入れ物という意味)
 * カスタムタグとしてmsh もつくっておく
 * vdeo   size vdeo flg version resSize inheritBox
 *  msh   size msh data
 *  stco
 *  stsc
 *  stsz
 *  stts
 *  stss
 * sond
 *  msh 
 *  stco
 *  stsc
 *  stsz
 *  stts
 * meta
 *  msh
 * @author taktod
 */
public class Version5Test {
	@Test
	public void analyzeTest() throws Exception {
		IFileReadChannel fc = FileReadChannel.openFileReadChannel("http://49.212.39.17/mario.mp4");
		IndexFileCreator analyzer = new IndexFileCreator(new File("output.tmp"));
		Atom atom = null;
		while((atom = analyzer.analyze(fc)) != null) {
			if(atom instanceof Moov) {
				break;
			}
		}
		analyzer.updatePrevTag();
		analyzer.checkDataSize();
		analyzer.close();
		IFileReadChannel tmp = FileReadChannel.openFileReadChannel(new File("output.tmp").getAbsolutePath());
		// ここで開始位置、video要素、audio要素等の指定が可能になります。
		FlvOrderModel orderModel = new FlvOrderModel(tmp, true, true, 6000);
		orderModel.addStartEvent(new IFlvStartEventListener() {
			@Override
			public void start(int responseSize) {
				System.out.println("responseSize:" + responseSize);
			}
		});
		// 書き込み対象作成
		FileChannel output = new FileOutputStream("target.flv").getChannel();
		// header情報
		orderModel.getFlvHeader().writeTag(output);
		List<Tag> tagList;
		// データを順に取り出します。nullになったら終わり
		while((tagList = orderModel.nextTagList(fc)) != null) {
			// 取り出したタグを書き出していきます。
			for(Tag tag : tagList) {
				tag.writeTag(output);
			}
		}
		output.close();
		tmp.close();
		fc.close();
	}
}
