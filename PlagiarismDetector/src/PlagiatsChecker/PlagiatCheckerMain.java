package PlagiatsChecker;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

/**
 * Application for fast and easy plagiarism detection. Can cross check many files
 * at once with reliable results.
 * 
 * @author Imanol Schlag (2014)
 * 
 */
public class PlagiatCheckerMain {

	static String filetype = ".class";
	/**
	 * Builds GUI and processes user input before showing the results.
	 */
	public static void main(String[] args) throws IOException {

		// System.out.println("PlagiatChecker by Imanol Schlag");
		File folder = null;

		//Choose File ending
		filetype = JOptionPane.showInputDialog(null, "Enter file ending to check: (e.g. java or class)");
		
		if(filetype == null) System.exit(0);
		else 
			filetype = filetype.trim();
		
		// choose folder
		JFileChooser chooser = new JFileChooser();
		chooser.setCurrentDirectory(new java.io.File("."));
		chooser.setDialogTitle("PlagiatChecker by Imanol Schlag");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			folder = chooser.getSelectedFile();
		} else {
			System.exit(0);
		}

		// choose wether deep search or not
		boolean deep = false;
		int reply = JOptionPane.showConfirmDialog(null, "deep file search?",
				"PlagiatChecker by Imanol Schlag", JOptionPane.YES_NO_OPTION);
		if (reply == JOptionPane.YES_OPTION) {
			deep = true;
		} else {
			deep = false;
		}

		// get files
		List<File> files = new ArrayList<>();
		files = listFilesForFolder(folder, deep);

		// inform user about number of files and time needed.
		class OneShotTask implements Runnable {
			int count;

			OneShotTask(int c) {
				count = c;
			}

			public void run() {
				int count = ((this.count - 1) * this.count) / 2;
				double time = (0.01 * (double) count);
				time = (double) Math.round(time * 100) / 100;
				int result = JOptionPane.showConfirmDialog(null,
						"Performing " + count
								+ " inspections.\nExpected time: " + time
								+ " seconds.",
						"PlagiatChecker by Imanol Schlag",
						JOptionPane.OK_CANCEL_OPTION);
				if(result == 2) {
					System.exit(0);
				}
			}
		}
		
		Thread t = new Thread(new OneShotTask(files.size()));
		t.start();

		// compare files
		List<Box> res = new ArrayList<>();
		while (files.size() > 1) {
			File next = files.remove(0);
			for (File f : files) {
				Box[] b = compare(next, f);
				res.add(b[0]);
				res.add(b[1]);
			}
		}

		// sort results and print
		JTable table = null;
		if (res.size() > 0) {
			Collections.sort(res);
			System.out.println();
			Object[] columnNames = { "Similar?", "Novelty", "File1", "File2" };
			Object[][] rowData = new Object[res.size()][4];
			int i = 0;
			for (Box b : res) {
				rowData[i][0] = b.b ? "yes" : "no";
				rowData[i][1] = b.n;
				rowData[i][2] = b.f1.getName();
				rowData[i][3] = b.f2.getName();
				i++;
			}
			table = new JTable(rowData, columnNames);

			JScrollPane scrollPane = new JScrollPane(table);
			JOptionPane.showMessageDialog(null, scrollPane,
					"PlagiatChecker by Imanol Schlag",
					JOptionPane.PLAIN_MESSAGE);

		} else {
			JOptionPane.showMessageDialog(null, "No files found.",
					"PlagiatChecker by Imanol Schlag",
					JOptionPane.PLAIN_MESSAGE);

		}
	}

	/**
	 * Returns all the files containing in this folder. Deep search possible.
	 * 
	 * @param folder
	 * @param deep search
	 * @return ArrayList of all the file objects.
	 */
	public static ArrayList<File> listFilesForFolder(File folder, boolean deep) {
		ArrayList<File> files = new ArrayList<File>();
		for (final File fileEntry : folder.listFiles()) {
			if (deep && fileEntry.isDirectory()) {
				files.addAll(listFilesForFolder(fileEntry, deep));
			} else if(fileEntry.isFile() && fileEntry.getName().length()>filetype.length() && fileEntry.getName().substring(fileEntry.getName().length()-filetype.length()).equals(filetype)){
				files.add(fileEntry);
			}
		}
		return files;
	}

	/**
	 * Compares the file length and compressed file length of two files for
	 * similarities.
	 * 
	 * @param first
	 *            file
	 * @param second
	 *            file
	 * @return Box Element containing both files and if the similarity has
	 *         reached a certain threshold.
	 */
	public static Box[] compare(File f1, File f2) {
		double threshold = 0.5;
		boolean log = false;

		long fLen1 = f1.length();
		String orig1 = removeWhiteSpace(removeComments(loadFile(f1)));
		long info1 = compress(orig1);

		if (log)
			System.out.println("File1: ");
		if (log)
			System.out.println("\tlength: " + fLen1);
		if (log)
			System.out.println("\tinfo: " + info1);

		long fLen2 = f2.length();
		String orig2 = removeWhiteSpace(removeComments(loadFile(f2)));
		long info2 = compress(orig2);

		if (log)
			System.out.println("File2: ");
		if (log)
			System.out.println("\tlength: " + fLen2);
		if (log)
			System.out.println("\tinfo: " + info2);

		long info12 = compress(orig1 + orig2);
		long info21 = compress(orig2 + orig1);
		if (log)
			System.out.println("Both: ");
		if (log)
			System.out.println("\tinfo12: " + info12);

		double novelty1 = ((double) info12 - (double) info2) / (double) info1;
		double novelty2 = ((double) info21 - (double) info1) / (double) info2;
		
		novelty1 = (double) Math.round(novelty1 * 1000) / 1000;
		novelty2 = (double) Math.round(novelty2 * 1000) / 1000;

		if (log)
			System.out.println("\nNovelty1: " + novelty1);
		if (log)
			System.out.println("Novelty2: " + novelty2);
		if (log)
			System.out.println();
		
		Box[] b = new Box[2];
		if (novelty1 < threshold) {
			b[0] = new Box(true, novelty1, f1, f2);
		} else {
			b[0] = new Box(false, novelty1, f1, f2);
		}
		
		if(novelty2 < threshold) {
			b[1] = new Box(true, novelty2, f2, f1);
		} else {
			b[1] = new Box(false, novelty2, f2, f1);
		}
		return b;
	}

	/**
	 * Removes Java single line comments, multi line comments.
	 * @param text with comments
	 * @return text without comments
	 */
	private static String removeComments(String orig) {
		//state 0 => no comment, state 1 => single line, state 1 => multi line
		int state = 0; 
		StringBuilder output = new StringBuilder();
		for (int i = 0; i < orig.length() - 1; i++) {
			
			if(state == 0 && orig.charAt(i) == '/') {
				if(orig.charAt(i+1) == '/') {
					state = 1;
				} else if(orig.charAt(i+1) == '*') {
					state = 2;
				}
			} else if(state == 1 && orig.charAt(i)=='\n') {
				state = 0;
				i++;
				continue;
			} else if(state == 2 && orig.charAt(i)=='*' && orig.charAt(i+1)=='/') {
				state = 0;
				i++;
				continue;
			}
			if(state == 0) 
				output.append(orig.charAt(i));
		}
		return output.toString();
	}
	
	/**
	 * Removes tabs and newLines from the original code.
	 * @param orig code
	 * @return code without newLines and tabs
	 */
	private static String removeWhiteSpace(String orig) {
		StringBuilder output = new StringBuilder();
		char[] removeList = { '\t', '\n' };
		next: for (int i = 0; i < orig.length() - 1; i++) {
			for(char c : removeList) {
				if( c == orig.charAt(i))
					continue next;
			}
			output.append(orig.charAt(i));
		}
		return output.toString();
	}

	/**
	 * Compresses the input with bzip2 and returns the number of bytes minus the
	 * bzip2 header.
	 * 
	 * @param file
	 *            content
	 * @return compressed size
	 */
	public static long compress(String data) {
		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			try {
				// Default is maximal compression
				OutputStream bzos = new BZip2CompressorOutputStream(os);
				try {
					bzos.write(data.getBytes());
				} finally {
					bzos.close();
				}
			} finally {
				os.close();
			}
			return os.size() - 37; // bzip2 header Abzug
		} catch (IOException e) {
			e.printStackTrace();
		}

		return -1;
	}

	/**
	 * Loads a specific file.
	 * 
	 * @param file
	 *            object
	 * @return file content
	 */
	public static String loadFile(File file) {
		try {
			InputStream is = new FileInputStream(file);
			BufferedReader r = new BufferedReader(new InputStreamReader(is));
			String text = "";
			String line;
			while ((line = r.readLine()) != null) {
				text += line+"\n";
			}
			r.close();
			return text;
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null,
					e.toString(),
					"PlagiatChecker by Imanol Schlag",
					JOptionPane.PLAIN_MESSAGE);
			System.exit(0);
		}
		return null;
	}

	/**
	 * Wrapper class for easy result handling.
	 * 
	 * @author Imanol
	 *
	 */
	static class Box implements Comparable<Box> {
		boolean b;
		double n;
		File f1, f2;

		Box(boolean bb, double nn, File ff1, File ff2) {
			b = bb;
			n = nn;
			f1 = ff1;
			f2 = ff2;
		}

		public int compareTo(Box b) {
			if (n < b.n)
				return -1;
			else if (n > b.n)
				return 1;
			else
				return 0;
		}

	}
	// // Read the compressed data
	// InputStream is = new FileInputStream(f);
	// try
	// {
	// InputStream bzis = new BZip2CompressorInputStream(is);
	// try
	// {
	// BufferedReader r = new BufferedReader(new InputStreamReader(bzis));
	// System.out.println(r.readLine());
	// }
	// finally
	// {
	// bzis.close();
	// }
	// }
	// finally
	// {
	// // Calling close here may mean that close will be called several times on
	// the
	// // same stream. That is safe.
	// is.close();
	// }
}
