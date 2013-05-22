package com.ttProject.media.test;

import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.nio.channels.IFileReadChannel;

public class Msh extends Atom {
	public Msh(int size, int position) {
		super("msh ", size, position);
	}
	@Override
	public void analyze(IFileReadChannel ch, IAtomAnalyzer analyzer)
			throws Exception {

	}
	@Override
	public String toString() {
		return super.toString("  ");
	}
}
