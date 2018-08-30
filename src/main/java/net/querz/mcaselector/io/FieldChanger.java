package net.querz.mcaselector.io;

import net.querz.mcaselector.Config;
import net.querz.mcaselector.changer.Field;
import net.querz.mcaselector.ui.ProgressTask;
import net.querz.mcaselector.util.Debug;
import net.querz.mcaselector.util.Timer;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class FieldChanger {

	public static void changeNBTFields(List<Field> fields, boolean force, ProgressTask progressChannel) {
		File[] files = Config.getWorldDir().listFiles((d, n) -> n.matches("^r\\.-?\\d+\\.-?\\d+\\.mca$"));
		if (files == null || files.length == 0) {
			return;
		}

		progressChannel.setMax(files.length);
		progressChannel.updateProgress(files[0].getName(), 0);

		for (int i = 0; i < files.length; i++) {
			MCAFilePipe.addJob(new MCAFieldChangeLoadJob(files[i], fields, force, i, progressChannel));
		}
	}

	public static class MCAFieldChangeLoadJob extends LoadDataJob {

		private ProgressTask progressChannel;
		private List<Field> fields;
		private boolean force;
		private int index;

		public MCAFieldChangeLoadJob(File file, List<Field> fields, boolean force, int index, ProgressTask progressChannel) {
			super(file);
			this.fields = fields;
			this.force = force;
			this.progressChannel = progressChannel;
			this.index = index;
		}

		@Override
		public void execute() {
			byte[] data = load();
			if (data != null) {
				MCAFilePipe.executeProcessData(new MCAFieldChangeProcessJob(getFile(), data, fields, force, index, progressChannel));
			}
		}
	}

	public static class MCAFieldChangeProcessJob extends ProcessDataJob {

		private ProgressTask progressChannel;
		private List<Field> fields;
		private boolean force;
		private int index;

		public MCAFieldChangeProcessJob(File file, byte[] data, List<Field> fields, boolean force, int index, ProgressTask progressChannel) {
			super(file, data);
			this.fields = fields;
			this.force = force;
			this.index = index;
			this.progressChannel = progressChannel;
		}

		@Override
		public void execute() {
			//load MCAFile
			Timer t = new Timer();
			MCAFile mca = MCAFile.readAll(getFile(), new ByteArrayPointer(getData()));
			mca.applyFieldChanges(fields, force);
			MCAFilePipe.executeSaveData(new MCAFieldChangeSaveJob(getFile(), mca, index, progressChannel));
			Debug.dumpf("took %s to apply field changes to %s", t, getFile().getName());
		}
	}

	public static class MCAFieldChangeSaveJob extends SaveDataJob<MCAFile> {

		private int index;
		private ProgressTask progressChannel;

		public MCAFieldChangeSaveJob(File file, MCAFile data, int index, ProgressTask progressChannel) {
			super(file, data);
			this.index = index;
			this.progressChannel = progressChannel;
		}

		@Override
		public void execute() {
			Timer t = new Timer();
			try {
				File tmpFile = File.createTempFile(getFile().getName(), null, null);

				boolean empty;

				try (RandomAccessFile raf = new RandomAccessFile(tmpFile, "rw")) {
					empty = !getData().saveAll(raf);
				}

				if (empty) {
					if (getFile().delete()) {
						Debug.dumpf("deleted empty region file %s", getFile().getAbsolutePath());
					} else {
						Debug.dumpf("could not delete empty region file %s", getFile().getAbsolutePath());
					}
				} else {
					Files.move(tmpFile.toPath(), getFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (Exception ex) {
				Debug.error(ex);
			}
			progressChannel.incrementProgress(getFile().getName());
			Debug.dumpf("took %s to save data to %s", t, getFile().getName());
		}
	}
}
