package jp.kshoji;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;

import replicatorg.app.Base;
import replicatorg.machine.MachineInterface;
import replicatorg.machine.MachineLoader;
import replicatorg.model.StringListSource;

/**
 * Play CNC Machine with MIDI
 * 
 * @author kshoji
 */
public class MidibotMain implements Receiver {
	/**
	 * fields about MIDI
	 */
	private static final int MAX_NOTES = 3;
	private static final int AUTO_STOP_MAX_COUNT = 5;
	Map<Integer, Integer> autoStopCounter = new HashMap<Integer, Integer>();
	Transmitter transmitter = null;
	Set<Integer> noteSet = new HashSet<Integer>();
	
	/**
	 * fields about Machine
	 */
	MachineInterface machine = null;
	double currentX, currentY, currentZ;
	double minX, minY, minZ;
	double maxX, maxY, maxZ;
	boolean addX, addY, addZ;

	/**
	 * compose vector length from x,y,z movement
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	private double calculateVectorLength(double x, double y, double z) {
		// 3D vector length
		return Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
	}
	
	/**
	 * Calculates distance for MIDI note number with 1 seconds.
	 * 
	 * @param note MIDI note number
	 * @return
	 */
	private double midiNoteToDistance(int note) {
		if (note < 0) {
			return 0;
		}
		// distance for 1 second
		return 10.0 * Math.pow(2.0, (note - 69) / 12.0);
	}
	
	/**
	 * Calculates distance per 60 seconds.
	 * 
	 * @param distancePer1Second
	 * @return
	 */
	private double distanceToFeedrate(double distancePer1Second) {
		return distancePer1Second * 60.0;
	}

	/**
	 * Generate G-Code string from MIDI note numbers, and length.
	 * 
	 * @param note1 MIDI note number (ignore when note1 < 0)
	 * @param note2 MIDI note number (ignore when note2 < 0)
	 * @param note3 MIDI note number (ignore when note3 < 0)
	 * @param length play length in second.
	 * @return G-Code string
	 */
	public String getGcodeStringFromNoteNumber(int note1, int note2, int note3, double length) {
		double x = midiNoteToDistance(note1);
		double y = midiNoteToDistance(note2);
		double z = midiNoteToDistance(note3);

		double fx = distanceToFeedrate(x);
		double fy = distanceToFeedrate(y);
		double fz = distanceToFeedrate(z);

		double feedVector = calculateVectorLength(fx, fy, fz);

		x *= length;
		y *= length;
		z *= length;

		changeAxisValue(x, y, z);
		if (!addX) {
			x = -x;
		}
		if (!addY) {
			y = -y;
		}
		if (!addZ) {
			z = -z;
		}

		java.text.DecimalFormat decimalFormat = new java.text.DecimalFormat("#.0000000000");
		return "G1 X" + decimalFormat.format(x) + " Y" + decimalFormat.format(y) + " Z" + decimalFormat.format(z) + " F" + decimalFormat.format(feedVector);
	}

	/**
	 * Select axis direction, with axis movement value.
	 * 
	 * @param x
	 * @param y
	 * @param z
	 */
	private void changeAxisValue(double x, double y, double z) {
		// boundary condition
		if (addX && currentX + x > maxX) {
			addX = false;
		} else if (!addX && currentX - x < minX) {
			addX = true;
		}
		if (addY && currentY + y > maxY) {
			addY = false;
		} else if (!addY && currentY - y < minY) {
			addY = true;
		}
		if (addZ && currentZ + z > maxZ) {
			addZ = false;
		} else if (!addZ && currentZ - x < minZ) {
			addZ = true;
		}

		if (addX) {
			currentX += x;
		} else {
			currentX -= x;
		}
		if (addY) {
			currentY += y;
		} else {
			currentY -= y;
		}
		if (addZ) {
			currentZ += z;
		} else {
			currentZ -= z;
		}
	}
	
	/**
	 * reset the Machine and parameters
	 */
	public void resetMachine() {
		if (machine != null) {
			// Move to home position, and center all axes.
			Vector<String> codes = new Vector<String>();
			codes.add("G21 (set units to mm)");
			codes.add("G90 (set positioning to absolute)");
			codes.add("G162 Z F500 (home Z axis maximum)");
			codes.add("G161 X Y F2500 (home XY axes minimum)");
			codes.add("M132 X Y Z A B (Recall stored home offsets for XYZAB axis)");
			codes.add("G1 X0 Y0 Z50 F1000");

			machine.buildDirect(new StringListSource(codes));
		}

		minX = -5.0;
		maxX = 5.0;
		
		minY = -5.0;
		maxY = 5.0;
		
		minZ = 0.0;
		maxZ = 10.0;

		currentX = 0.0;
		currentY = 0.0;
		currentZ = 0.0;
	}
	
	/**
	 * set up swing dialog
	 * 
	 * @param infoMap MIDI Transmitter device information
	 */
	private void setUpSwingDialog(final Map<Info, Transmitter> infoMap) {
		final JComboBox jComboBox = new JComboBox();
		for (Info info : infoMap.keySet()) {
			jComboBox.addItem(info);
		}
		
		JPanel comboBoxPanel = new JPanel();
		comboBoxPanel.add(jComboBox);
		
		final JButton startButton = new JButton();
		startButton.setAction(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				jComboBox.setEnabled(false);
				startButton.setEnabled(false);
				setEnabled(false);
				
				Info selectedItem = (Info) jComboBox.getSelectedItem();
				selectTransmitter(infoMap.get(selectedItem));
				
				machine = getMachine();
				resetMachine();
				
				MachineThread machineThread = new MachineThread();
				machineThread.start();
			}
		});
		startButton.setText("Choose & start to play.");
		
		JPanel startButtonPanel = new JPanel();
		startButtonPanel.add(startButton);
		
		final JButton resetButton = new JButton();
		resetButton.setAction(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				resetMachine();
			}
		});
		resetButton.setText("Reset machine.");
		
		JPanel resetButtonPanel = new JPanel();
		resetButtonPanel.add(resetButton);
		
		JFrame frame = new JFrame("Choose MIDI instrument");
		frame.setSize(300, 200);
		frame.setLocationRelativeTo(null); // show in center
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		Container container = frame.getContentPane();
		container.add(comboBoxPanel, BorderLayout.NORTH);
		container.add(startButtonPanel, BorderLayout.CENTER);
		container.add(resetButtonPanel, BorderLayout.SOUTH);
		
		frame.setVisible(true);
	}

	public static void main(String[] args) {
		MidibotMain midibot = new MidibotMain();
		Map<Info, Transmitter> infoMap = midibot.listUpTransmitterInfo();
		midibot.setUpSwingDialog(infoMap);
	}
	
	/**
	 * list up MIDI Transmitter information.
	 * 
	 * @return
	 */
	public Map<Info, Transmitter> listUpTransmitterInfo() {
		Map<Info, Transmitter> result = new HashMap<Info, Transmitter>();
		Info[] infos = MidiSystem.getMidiDeviceInfo();
		for (Info info : infos) {
			try {
				MidiDevice device = MidiSystem.getMidiDevice(info);
				if (device != null) {
					// MIDIã@äÌÇÃTransmitter
					Transmitter trans = device.getTransmitter();
					if (trans != null) {
						// ó·äOÇ™èoÇ»Ç©Ç¡ÇΩÇÁí«â¡
						result.put(info, trans);
					}
				}
			} catch (MidiUnavailableException e) {
				System.err.println(e.getMessage() + ":" + info);
			}
		}
		return result;
	}
	
	/**
	 * set the transmitter to use.
	 * 
	 * @param transmitter
	 */
	private void selectTransmitter(Transmitter transmitter) {
		this.transmitter = transmitter;
		this.transmitter.setReceiver(this);
	}
	
	/**
	 * load the machine last used
	 * 
	 * @return
	 */
	public MachineInterface getMachine() {
		MachineLoader machineLoader = new MachineLoader();
		String name = Base.preferences.get("machine.name", null);
		System.out.println("machine.name: " + name);
		boolean loaded = machineLoader.load(name);
		System.out.println("loaded: " + loaded);
		if (!loaded) {
			throw new IllegalStateException("Couldn't load machine.");
		}
		
		String targetPort = Base.preferences.get("serial.last_selected", null);
		System.out.println("targetPort: " + targetPort);
		if (targetPort == null) {
			throw new NullPointerException("Couldn't find target port.");
		}
		machineLoader.connect(targetPort);
		return machineLoader.getMachine();
	}
	
	/**
	 * Machine controlling thread
	 * 
	 * @author K.Shoji
	 */
	class MachineThread extends Thread {
		@Override
		public void run() {
			super.run();

			Vector<String> codes = new Vector<String>();
			while (true) {
				try {
					sleep(100);

					// add notes
					ArrayList<Integer> notes = new ArrayList<Integer>();
					synchronized (noteSet) {
						for (Integer note : noteSet) {
							Integer counter = autoStopCounter.get(note);
							if (counter == null) {
								autoStopCounter.put(note, 1);
								counter = 1;
							} else {
								autoStopCounter.put(note, counter + 1);
							}
							if (counter > AUTO_STOP_MAX_COUNT) {
								continue;
							}
							notes.add(note);
						}

						// auto stop notes
						List<Integer> removeList = new ArrayList<Integer>();
						for (Integer note : noteSet) {
							Integer counter = autoStopCounter.get(note);
							if (counter == null) {
								continue;
							}
							if (counter > AUTO_STOP_MAX_COUNT) {
								removeList.add(note);
							}
						}
						for (Integer integer : removeList) {
							noteSet.remove(integer);
							autoStopCounter.put(integer, 0);
						}
					}

					if (notes.size() < 1) {
						// do nothing...
						continue;
					}

					// play sound 100milliseconds
					String gcodeString = getGcodeStringFromNoteNumber(notes.get(0), notes.size() > 1 ? notes.get(1) : -1, notes.size() > 2 ? notes.get(2) : -1, 0.1);

					System.out.println(gcodeString);
					codes.clear();
					codes.add(gcodeString);
					machine.buildDirect(new StringListSource(codes));

				} catch (ConcurrentModificationException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
					machine.disconnect();
					break;
				}
			}
		}
	}
	
	/**
	 * Action. must implement actionPerformed method.
	 * 
	 * @author K.Shoji
	 */
	abstract class AbstractAction implements Action {
		@Override
		public abstract void actionPerformed(ActionEvent arg0);
		
		@Override
		public void setEnabled(boolean arg0) {
		}
		
		@Override
		public final void removePropertyChangeListener(PropertyChangeListener arg0) {
		}
		
		@Override
		public final void putValue(String arg0, Object arg1) {
		}
		
		@Override
		public boolean isEnabled() {
			return true;
		}
		
		@Override
		public final Object getValue(String arg0) {
			return null;
		}
		
		@Override
		public final void addPropertyChangeListener(PropertyChangeListener arg0) {
		}
	}
	
	/**
	 * called when MIDI signal coming
	 * 
	 * @see {@code Receiver#send(MidiMessage, long)}
	 */
	@Override
	public void send(MidiMessage message, long timeStamp) {
		if (message instanceof ShortMessage) {
			ShortMessage shortMessage = ((ShortMessage) message);

			int data1 = shortMessage.getData1();

			switch (shortMessage.getCommand()) {
			case ShortMessage.NOTE_ON:
				if (shortMessage.getData2() != 0) {
					synchronized (noteSet) {
							if (!noteSet.contains(data1) && noteSet.size() < MAX_NOTES) {
							noteSet.add(data1);
							System.out.println("MIDI note on :" + data1);
						}
					}
				} else {
					synchronized (noteSet) {
						noteSet.remove(data1);
					}
					System.out.println("MIDI note off:" + data1);
				}
				break;
			case ShortMessage.NOTE_OFF:
				synchronized (noteSet) {
					noteSet.remove(data1);
					autoStopCounter.put(data1, 0);
				}
				System.out.println("MIDI note off:" + data1);
				break;
			}
		}
	}
	
	/**
	 * maybe called when MIDI closed.
	 * 
	 * @see {@code Receiver#close()}
	 */
	@Override
	public void close() {
		System.out.println("MIDI closed.");
	}
}
