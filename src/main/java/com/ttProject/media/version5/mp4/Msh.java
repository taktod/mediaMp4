package com.ttProject.media.version5.mp4;

import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.nio.channels.IFileReadChannel;

/**
 * mediaSequenceHeader用のatom
 * @author taktod
 */
public class Msh extends Atom {
	/**
	 * コンストラクタ
	 * @param size
	 * @param position
	 */
	public Msh(int size, int position) {
		super("msh ", size, position);
	}
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void analyze(IFileReadChannel ch, IAtomAnalyzer analyzer)
		throws Exception {
	}
}