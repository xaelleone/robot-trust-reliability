import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Scanner;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.theeyetribe.client.GazeManager;
import com.theeyetribe.client.GazeManager.ApiVersion;
import com.theeyetribe.client.GazeManager.ClientMode;
import com.theeyetribe.client.IGazeListener;
import com.theeyetribe.client.data.GazeData;

import acm.graphics.GImage;
import acm.graphics.GLabel;
import acm.graphics.GLine;
import acm.graphics.GObject;
import acm.graphics.GOval;
import acm.graphics.GRect;
import acm.program.GraphicsProgram;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;

public class Tracker extends GraphicsProgram implements MouseMotionListener {
	private static final long serialVersionUID = 1L;
	public static final int APPLICATION_HEIGHT = 1000;
	public static final int APPLICATION_WIDTH = TrackerConstants.SCREEN_DIVISION_X;
	public static final int APPLICATION_X = 360;
	private GLabel timer;
	private GRect tracker;
	private int counter = 0;
	private GRect[] automationRecommendation;
	private ArrayList<GImage> imagesToUse;
	public DataAggregator entries;
	private Physics p;
	private boolean running = false;
	private JButton toPractice;
	private ArrayList<String> practiceText = new ArrayList<String>();
	private Controller joystick;
	private double startTime;
	private ArrayList<GObject> cursorSwarm;
	private int inCircleSteps = 0;
	private int totalTimeSteps = 0;
	private int toggles = 0;
	private AudioPlayer audio = new AudioPlayer();
	private String fileName;
	private double pauseStart;
	private double pauseBank = 0;
	private boolean isControlRun = false;
	private JButton close = new JButton("Close");
	private GRect[] blockers;
	private int preEnteredAnswer = -1;
	private double timeSpent;
	private ArrayList<GObject> pList = new ArrayList<GObject>();
	private ArrayList<GObject> tList = new ArrayList<GObject>();
	
	/*
	 * TODO: 
	 * change data recording practices
	 */
	
	public static void main (String[] args) {
		new Tracker().start();
	}
	
	public void init () {
		UIManager.put("OptionPane.messageFont", new Font("Arial", Font.PLAIN, 20));
		initializeControllers();
		String[] stringForm = new String[TrackerConstants.AUTOMATION_CORRECT_PERCENTAGES.length];
		for (int i = 0; i < stringForm.length; i++) {
			stringForm[i] = Double.toString((int)(TrackerConstants.AUTOMATION_CORRECT_PERCENTAGES[i] * 100));
		}
		fileName = (String)JOptionPane.showInputDialog(this, "File name (no extension):", "Setup", JOptionPane.PLAIN_MESSAGE, null, null, null);
		initScreen();
	}
	
	private void initMainScreen () {
		this.removeAll();
		entries = new DataAggregator(System.currentTimeMillis(), fileName, reliability, isBinaryAlarm, isControlRun, true);
		
		imagesToUse = new ArrayList<GImage>();
		
		putRandomImages();
		addTrialLabels();
		
		tracker = new GRect(0, 0, APPLICATION_WIDTH, APPLICATION_HEIGHT - TrackerConstants.TRACKER_AREA_BOTTOM);
		tracker.setColor(new Color(95, 166, 195));
		tracker.setFilled(true);
		tList.add(tracker);
		this.add(tracker);
		GRect trackerGround = new GRect(0, TrackerConstants.HORIZON_Y, APPLICATION_WIDTH, APPLICATION_HEIGHT - TrackerConstants.TRACKER_AREA_BOTTOM - TrackerConstants.HORIZON_Y);
		trackerGround.setColor(new Color(170, 140, 100));
		trackerGround.setFilled(true);
		tList.add(trackerGround);
		this.add(trackerGround);
		addTarget();
		initCursorSwarm();
		initRecommender();
		
		if (timer == null) {
			timer = new GLabel("0");
			timer.setColor(Color.WHITE);
			timer.setFont(new Font("Arial", Font.BOLD, 14));
			timer.setLocation(TrackerConstants.TIMER_Y, TrackerConstants.TIMER_Y);
			tList.add(timer);
			pList.add(timer);
			this.add(timer);
		}
		
		//addTooltip(); deprecated thing that says press z or x or whatever
		
		p = new Physics();
		
		this.add(close, APPLICATION_WIDTH - 100, 20);
	}
	
	private void initializeControllers() {
		ControllerEnvironment ce = ControllerEnvironment.getDefaultEnvironment(); 
		Controller[] cs = ce.getControllers();
		joystick = cs[TrackerConstants.JOYSTICK_INPUT_NUMBER]; //SUBJECT TO CHANGE.
	}
	
	private void addTrialLabels () {
		trialNumber = new GLabel("Trial: " + counter + "/" + TrackerConstants.TRIAL_COUNT);
		if (inPracticeMode) {
			trialNumber.setLabel(practiceText.get(0));
		}
		trialNumber.setFont(new Font("Arial", Font.PLAIN, 24));
		trialNumber.setLocation(TrackerConstants.RECOMMENDER_BUFFER, APPLICATION_HEIGHT - TrackerConstants.TRACKER_AREA_BOTTOM + TrackerConstants.RECOMMENDER_BUFFER);
		otherPracticeTip = new GLabel("");
		otherPracticeTip.setFont(new Font("Arial", Font.PLAIN, 24));
		otherPracticeTip.setLocation(TrackerConstants.RECOMMENDER_BUFFER, TrackerConstants.LINE_HEIGHT + APPLICATION_HEIGHT - TrackerConstants.TRACKER_AREA_BOTTOM + TrackerConstants.RECOMMENDER_BUFFER);
		pList.add(trialNumber);
		pList.add(otherPracticeTip);
		tList.add(trialNumber);
		tList.add(otherPracticeTip);
		this.add(trialNumber);
		this.add(otherPracticeTip);
		running = true;
	}
	
	private void initBlockers () {
		blockers = new GRect[1];
		//blockers[0] = new GRect(APPLICATION_WIDTH - TrackerConstants.RIGHT_BUFFER, 0, TrackerConstants.RIGHT_BUFFER, APPLICATION_HEIGHT);
		//blockers[1] = new GRect(TrackerConstants.SCREEN_DIVISION_X - TrackerConstants.RIGHT_BUFFER, 0, TrackerConstants.RIGHT_BUFFER, APPLICATION_HEIGHT);
		blockers[0] = new GRect(0, APPLICATION_HEIGHT - TrackerConstants.TRACKER_AREA_BOTTOM, APPLICATION_WIDTH, TrackerConstants.RECOMMENDER_BUFFER * 3 / 2);
		for (GRect g : blockers) {
			g.setColor(Color.WHITE);
			g.setFilled(true);
			g.sendToFront();
			tList.add(g);
			this.add(g);
		}
		trialNumber.sendToFront();
		otherPracticeTip.sendToFront();
	}
	
	private void initCursorSwarm () {
		Tuple o = new Tuple(APPLICATION_WIDTH / 2, Physics.ORIGIN_Y);
		double unit = TrackerConstants.CURSOR_SIZE / 2;
		double sep = TrackerConstants.CURSOR_SIZE * 3;
		ArrayList<GLine> shapes = new ArrayList<GLine>();
		shapes = addCross(shapes, o, unit);
		for (int i = 1; i <= 3; i++) {
			shapes = addCross(shapes, new Tuple(o.x - sep * i, o.y), unit / 2);
			shapes = addCross(shapes, new Tuple(o.x + sep * i, o.y), unit / 2);
			shapes = addCross(shapes, new Tuple(o.x, o.y - sep * i), unit / 2);
			shapes = addCross(shapes, new Tuple(o.x, o.y + sep * i), unit / 2);
		}
		shapes = inwardDashes(shapes, new Tuple(o.x - sep, o.y), sep, unit / 2, false);
		shapes = inwardDashes(shapes, new Tuple(o.x + sep, o.y), sep, unit / 2, false);
		shapes = inwardDashes(shapes, new Tuple(o.x, o.y - sep), sep, unit / 2, true);
		shapes = inwardDashes(shapes, new Tuple(o.x, o.y + sep), sep, unit / 2, true);
		for (GLine g : shapes) {
			g.setColor(Color.YELLOW);
			tList.add(g);
			this.add(g);
		}
		initBlockers();
	}
	
	private ArrayList<GLine> inwardDashes (ArrayList<GLine> shapes, Tuple center, double sep, double unit, boolean horiz) {
		if (horiz) {
			shapes.add(new GLine(center.x - sep, center.y, center.x - sep + unit, center.y));
			shapes.add(new GLine(center.x + sep, center.y, center.x + sep - unit, center.y));
		}
		else {
			shapes.add(new GLine(center.x, center.y - sep, center.x, center.y - sep + unit));
			shapes.add(new GLine(center.x, center.y + sep, center.x, center.y + sep - unit));
		}
		return shapes;
	}
	
	private ArrayList<GLine> addCross (ArrayList<GLine> shapes, Tuple loc, double arm) {
		shapes.add(new GLine(loc.x - arm, loc.y, loc.x + arm, loc.y));
		shapes.add(new GLine(loc.x, loc.y - arm, loc.x, loc.y + arm));
		return shapes;
	}
	
	private void initRecommender () {
		automationRecommendation = new GRect[isBinaryAlarm ? 2 : 4];
		int startX = TrackerConstants.RECOMMENDER_BUFFER;
		int startY = APPLICATION_HEIGHT - TrackerConstants.TRACKER_AREA_BOTTOM + TrackerConstants.RECOMMENDER_BUFFER * 7 / 4;
		int width = (APPLICATION_WIDTH - TrackerConstants.RECOMMENDER_BUFFER * 2) / automationRecommendation.length;
		int height = TrackerConstants.RECOMMENDER_BUFFER * 3 / 2;
		Color c;
		for (int i = 0; i < automationRecommendation.length; i++) {
			automationRecommendation[i] = new GRect(startX, startY, width, height);
			automationRecommendation[i].setFilled(true);
			c = isControlRun ? Color.WHITE : isBinaryAlarm ? QuotaSet.BINARY_COLORS[i] : QuotaSet.LIKELIHOOD_COLORS[i];
			automationRecommendation[i].setColor(c);
			automationRecommendation[i].setFillColor(modifyAlpha(c, TrackerConstants.INACTIVE_ALARM_ALPHA));
			tList.add(automationRecommendation[i]);
			this.add(automationRecommendation[i]);
			startX += width + 1;
		}
	}
	
	private Color modifyAlpha (Color c, int alpha) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}
	
	private void changeColor (Color c) {
		for (int i = 0; i < automationRecommendation.length; i++) {
			if ((isBinaryAlarm && QuotaSet.BINARY_COLORS[i].equals(c)) || !isBinaryAlarm && QuotaSet.LIKELIHOOD_COLORS[i].equals(c)) {
				automationRecommendation[i].setFillColor(modifyAlpha(automationRecommendation[i].getColor(), TrackerConstants.ACTIVE_ALARM_ALPHA));
			}
			else {
				automationRecommendation[i].setFillColor(modifyAlpha(automationRecommendation[i].getColor(), TrackerConstants.INACTIVE_ALARM_ALPHA));
			}
		}
	}
	
	private void initScreen () {
		try {
			Scanner fin = new Scanner(new BufferedReader(new FileReader("initScreenText")));
			GLabel temp;
			for (int i = 0; fin.hasNextLine(); i++) {
				temp = new GLabel(fin.nextLine());
				temp.setFont(new Font("Arial", Font.PLAIN, 24));
				temp.setLocation(TrackerConstants.INIT_SCREEN_BUFFER, i * TrackerConstants.LINE_HEIGHT + TrackerConstants.INIT_SCREEN_BUFFER);
				add(temp);
			}
			fin.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		toPractice = new JButton("Start");
		toPractice.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				practice();
			}
		});
		this.add(toPractice, TrackerConstants.INIT_SCREEN_BUFFER, 8 * TrackerConstants.LINE_HEIGHT + TrackerConstants.INIT_SCREEN_BUFFER);
	}
	
	private void addEntry (int answer) {
		entries.add(new Entry(allTrials.get(counter - 1), answer, inCircleSteps * 1.0 / totalTimeSteps, startTime, counter, currentGazeDataSet, this.onTrackerChecks * 1.0 / this.totalTrackerChecks, this.toggles, new Tuple(this.getGCanvas().getLocationOnScreen()), inPracticeMode && counter <= TrackerConstants.TRACKER_ONLY_PRACTICE_COUNT, timeSpent));
	}
	
	private void addTarget () {
		cursorSwarm = new ArrayList<GObject>();
		double unit = TrackerConstants.TARGET_SIZE;
		double sep = unit * 3d / 4;
		Tuple o = new Tuple(APPLICATION_WIDTH / 2, (APPLICATION_HEIGHT - TrackerConstants.TRACKER_AREA_BOTTOM) / 2);
		cursorSwarm.add(new GOval(o.x - unit, o.y - unit, unit * 2, unit * 2));
		cursorSwarm.add(new GOval(1 + o.x - unit, 1 + o.y - unit, unit * 2 - 2, unit * 2 - 2));
		cursorSwarm.add(new GLine(o.x - sep, o.y, o.x - sep - unit / 2, o.y));
		cursorSwarm.add(new GLine(o.x + sep, o.y, o.x + sep + unit / 2, o.y));
		cursorSwarm.add(new GLine(o.x, o.y - sep, o.x, o.y - sep - unit / 2));
		cursorSwarm.add(new GLine(o.x, o.y + sep, o.x, o.y + sep + unit / 2));
		for (GObject s : cursorSwarm) {
			s.setColor(Color.GREEN);
			this.add(s);
			tList.add(s);
		}
	}
	
	public void mouseMoved (MouseEvent e) {
		/*if (mousePos != null) {
			p.mouseDiff = new Tuple(e.getX() - mousePos.x, e.getY() - mousePos.y);
		}
		mousePos = new Tuple(e.getX(), e.getY());*/
	}
	
	private void moveJoystick (double x, double y) {
		p.mouseDiff = (new Tuple(x, y)).scalarMultiple(TrackerConstants.JOYSTICK_SENSITIVITY);
	}
	
	/*public void keyPressed (KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_Z || e.getKeyCode() == KeyEvent.VK_Y) {
			audio.play("sounds/ack.wav");
			audio = new AudioPlayer();
			addEntry(0);
		}
		if (e.getKeyCode() == KeyEvent.VK_X || e.getKeyCode() == KeyEvent.VK_N) {
			audio.play("sounds/ack.wav");
			audio = new AudioPlayer();
			addEntry(1);
		}
	}*/
	
	private void pause () {
		running = false;
		pauseStart = System.currentTimeMillis();
	}
	
	private void unpause () {
		running = true;
		pauseBank += System.currentTimeMillis() - pauseStart;
	}
	
	private void createTemporaryDuplicateLabelForDuration (GLabel label, String s, double duration) {
		GLabel temp = new GLabel(s);
		temp.setFont(new Font("Arial", Font.PLAIN, 120));
		temp.setColor(Color.RED);
		temp.setLocation(APPLICATION_WIDTH / 2 - temp.getWidth() / 2, APPLICATION_HEIGHT / 2 - TrackerConstants.RECOMMENDER_BUFFER * 2);
		add(temp);
		this.repaint();
		double start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < duration) {
			if (System.currentTimeMillis() % 1000 == 0) System.out.print("");
		}
		remove(temp);
	}
	
	private void countdown () {	
		audio.play("sounds/ready.wav");
		audio = new AudioPlayer();
		createTemporaryDuplicateLabelForDuration(trialNumber, "READY", 500);
		createTemporaryDuplicateLabelForDuration(trialNumber, "SET", 500);
		createTemporaryDuplicateLabelForDuration(trialNumber, "GO!", 500);
	}
	
	private void incrementTrialNumber () { 
		pause();
		if (!inPracticeMode || (counter > TrackerConstants.TRACKER_ONLY_PRACTICE_COUNT && counter <= TrackerConstants.TRACKER_ONLY_PRACTICE_COUNT + TrackerConstants.EFFECTIVE_PRACTICE_TRIAL_COUNT)) {
			if (entries.getMostRecentEntry().getScore() > 0) {
				audio.play("sounds/goodjob.wav");
				audio = new AudioPlayer();
			}
			else {
				audio.play("sounds/lousyjob.wav");
				audio = new AudioPlayer();
			}	
		}
		if (counter >= 1) {
			displayRoundFeedback();
		}
		if (!inPracticeMode || counter > TrackerConstants.TRACKER_ONLY_PRACTICE_COUNT) {
			displayAndLogPolls();
		}
		if (!inPracticeMode && counter == TrackerConstants.TRIAL_COUNT) {
			running = false;
			entries.closeAll();
			JOptionPane.showMessageDialog(this, "The experiment is over. Thank you for participating.",
					"End of practice",
					JOptionPane.PLAIN_MESSAGE
			);
			this.exit();
		}
		if (counter % 50 == 0 && counter != 0) {
			JOptionPane.showMessageDialog(this, "You may take a short break before continuing.",
					"Break",
					JOptionPane.PLAIN_MESSAGE
			);
		}
		if (counter == TrackerConstants.TRACKER_ONLY_PRACTICE_COUNT + TrackerConstants.EFFECTIVE_PRACTICE_TRIAL_COUNT && inPracticeMode) {
			inPracticeMode = false;
			counter = 0; //will get incremented
			loadImages();
			JOptionPane.showMessageDialog(this, "The practice phase is over. You are about to begin the experiment.",
					"End of practice",
					JOptionPane.PLAIN_MESSAGE
			);
			new PracticeDataOutput(entries).output();
			entries = new DataAggregator(System.currentTimeMillis(), fileName, reliability, isBinaryAlarm, isControlRun, false);
		}
		countdown();
		unpause();
		counter++;
		if (inPracticeMode) {
			trialNumber.setLabel("Trial: " + counter + "/" + (TrackerConstants.TRACKER_ONLY_PRACTICE_COUNT + TrackerConstants.EFFECTIVE_PRACTICE_TRIAL_COUNT));
			otherPracticeTip.setLabel("Score: " + formatScore(entries.getScore(), false) + "/" + 15 * counter);
			/*trialNumber.setLabel(practiceText.get(counter * 2));
			otherPracticeTip.setLabel(practiceText.get(counter * 2 + 1));*/
		}
		else {
			trialNumber.setLabel("Trial: " + counter + "/" + TrackerConstants.TRIAL_COUNT);
			otherPracticeTip.setLabel("Score: " + formatScore(entries.getScore(), false) + "/" + 15 * counter);
			this.currentGazeDataSet = new ArrayList<EyeEntry>();
		} 
	}
	
	private void nextRound () { //0: spotted enemy, 1: all clear, -1: no answer
		if (!inPracticeMode || counter >= 1) addEntry(preEnteredAnswer);
		incrementTrialNumber();
		putRandomImages();
		pauseBank = 0;
		startTime = System.currentTimeMillis();
		totalTimeSteps = 0;
		inCircleSteps = 0;
		this.totalTrackerChecks = 0;
		this.onTrackerChecks = 0;
		this.toggles = 0;
		this.timeSpent = TrackerConstants.TRIAL_LENGTH_MS;
		preEnteredAnswer = -1;
	}
	
	private void moveCursorSwarm (double x, double y) {
		for (GObject g : cursorSwarm) {
			//BOUNDS CHECK
			if (g.getLocation().getY() > APPLICATION_HEIGHT - TrackerConstants.TRACKER_AREA_BOTTOM || g.getLocation().getX() + (g.getWidth() / 2) < 0) {
				g.setVisible(false);
			}
			if (this.tracker.isVisible() && g.getLocation().getY() < APPLICATION_HEIGHT - TrackerConstants.TRACKER_AREA_BOTTOM && g.getLocation().getX() > 0) {
				g.setVisible(true);
			}
			g.move(x, y);
		}
	}
	
	private void resetCursorSwarm () {
		for (GObject g : cursorSwarm) {
			this.remove(g);
		}
		for (GRect b : blockers) {
			this.remove(b);
		}
		cursorSwarm = new ArrayList<GObject>();
		addTarget();
		initBlockers();
	}
	
	private void displayAndLogPolls () {
		ArrayList<Dictionary<Integer, JLabel>> labels = new ArrayList<Dictionary<Integer, JLabel>>();
		String[] messages = new String[TrackerConstants.NUM_POLLS];
		Dictionary<Integer, JLabel> temp;
		for (int i = 0; i < TrackerConstants.NUM_POLLS; i++) {
			temp = new Hashtable<Integer, JLabel>();
			switch (i) {
			case 0: //UGLY UGLY HOTFIX, WILL CHANGE EVENTUALLY
				messages[0] = "How confident are you in completing the task without the detector?";
				temp.put(0, new JLabel("<html>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;0<br>Not confident at all</html>"));
				temp = addIntermediateValues(temp);
				temp.put(100, new JLabel("<html>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;100<br>Absolutely confident</html>"));
				break;
			case 1:
				messages[1] = "How reliable are the automated detector's recommendations?";
				temp.put(0,  new JLabel("<html>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;0<br>Not reliable at all</html>"));
				temp = addIntermediateValues(temp);
				temp.put(100,  new JLabel("<html>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;100<br>Absolutely reliable</html>"));
				break;
			default:
				messages[2] = "How much do you trust the automated detector's recommendations?";
				temp.put(0, new JLabel("<html>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;0<br>I don't trust it at all</html>"));
				temp = addIntermediateValues(temp);
				temp.put(100, new JLabel("<html>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;100<br>I absolutely trust it</html>"));
			}
			labels.add(temp);
		}
		entries.addPollResult(displayPoll(messages, labels));
	}
	
	private Dictionary<Integer, JLabel> addIntermediateValues (Dictionary<Integer, JLabel> dict) {
		for (int i = 10; i <= 90; i+= 10) {
			dict.put(i, new JLabel(Integer.toString(i)));
		}
		return dict;
	}
	
	private PollResult displayPoll (String[] message, ArrayList<Dictionary<Integer, JLabel>> labels) {
		JFrame parent = new JFrame();
		JOptionPane optionPane = new JOptionPane();
		JSlider[] sliders = new JSlider[TrackerConstants.NUM_POLLS];
		Object[] thingsOnOptionPane = new Object[TrackerConstants.NUM_POLLS * 2];
		for (int i = 0; i < sliders.length; i++) {
			sliders[i] = getSlider(optionPane, labels.get(i));
			sliders[i].setMajorTickSpacing(10);
			sliders[i].setPaintTicks(true);
			sliders[i].addChangeListener(new ChangeListener() {
		        public void stateChanged(ChangeEvent ce) {
		            JSlider slider = (JSlider)ce.getSource();
		            if (!slider.getValueIsAdjusting()) {
		                slider.setToolTipText(Integer.toString(slider.getValue()));
		            }
		        }
		    });
			thingsOnOptionPane[i * 2] = message[i];
			thingsOnOptionPane[i * 2 + 1] = sliders[i];
		}
		optionPane.setMessage(thingsOnOptionPane);
		optionPane.setMessageType(JOptionPane.PLAIN_MESSAGE);
		optionPane.setOptionType(JOptionPane.OK_CANCEL_OPTION);
		JDialog dialog = optionPane.createDialog(parent, "Question");
		dialog.setVisible(true);
		PollResult p = new PollResult(counter);
		for (int i = 0; i < sliders.length; i++) {
			p.results.add(sliders[i].getValue());
		}
		return p;
	}

	private JSlider getSlider(final JOptionPane optionPane, Dictionary<Integer, JLabel> labels) {
	    JSlider slider = new JSlider();
	    slider.setMajorTickSpacing(25);
	    slider.setPaintTicks(true);
	    slider.setPaintLabels(true);
	    slider.setLabelTable(labels);
	    slider.setValue(50);
	    ChangeListener changeListener = new ChangeListener() {
	    	public void stateChanged(ChangeEvent changeEvent) {
	    		JSlider theSlider = (JSlider) changeEvent.getSource();
	    		if (!theSlider.getValueIsAdjusting()) {
	    			optionPane.setInputValue(new Integer(theSlider.getValue()));
	    		}
	    	}
	    };
	    slider.addChangeListener(changeListener);
	    return slider;
	}
	
	public static String getRecommendationString (Color c) {
		for (int i = 0; i < QuotaSet.LIKELIHOOD_COLORS.length; i++) {
			if (c.equals(QuotaSet.LIKELIHOOD_COLORS[i])) {
				return QuotaSet.RECOMMENDATION_STRINGS[i];
			}
		}
		return "???"; //unclear what occurred
	}
	
	private String formatScore (double d, boolean addPlus) {
		String s = Long.toString(Math.round(d));
		if (addPlus && (int)d >= 0) s = "+" + s;
		return s;
	}
	
	private void displayRoundFeedback () {
		Entry last = entries.getMostRecentEntry();
		JOptionPane.showMessageDialog(this, new JLabel("<html><font size=5>" + ((inPracticeMode && counter <= TrackerConstants.TRACKER_ONLY_PRACTICE_COUNT) ? "" : (isControlRun ? "" : "Detector recommendation: " + getRecommendationString(last.t.color) + "<br>") +
				(last.outOfTime ? "You ran out of time." : "Your identification: " + 
				(last.identifiedEnemy ? "DANGER" : "CLEAR") + "<br>" + 
				"You are " + (last.identifiedEnemy == last.t.containsEnemy ? "<font color=green><b>CORRECT</b></font>." : "<font color=red><b>INCORRECT</b></font>")) + "<br>" + 
				"Your detection score: " + formatScore(entries.getDetectionScore(), false) + " <b>(" + formatScore(last.getDetectionScore(), true) + ")</b> <br>") +
				"Your tracker score: " + formatScore(entries.getTrackerScore(), false) + " <b>(" + formatScore(last.getTrackerScore(), true) + ")</b> <br>" +
				"Total score: " + formatScore(entries.getScore(), false) + " <b>(" + formatScore(last.getScore(), true) + ")</b><br></font></html>"),
				"Results",
				JOptionPane.PLAIN_MESSAGE
		);
	}
	
	private void closeProgram () {
		this.exit();
	}
	
	private void changeScreenLeft () {
		for (GObject t : tList) {
			t.setVisible(false);
		}
		for (GObject p : pList) {
			p.setVisible(true);
		}
		timer.setColor(Color.BLACK);
	}
	
	private void changeScreenRight () {
		for (GObject p : pList) {
			p.setVisible(false);
		}
		for (GObject t : tList) {
			t.setVisible(true);
		}
		timer.setColor(Color.WHITE);
	}
	
	public boolean isOnTrackerScreen () {
		return this.tracker.isVisible();
	}
	
	public void run () {
		
		close.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (entries != null)
					entries.closeAll();
				closeProgram();
			}
		});
		/*final GazeManager gm = GazeManager.getInstance();
        boolean success = gm.activate(ApiVersion.VERSION_1_0, ClientMode.PUSH);
        final IGazeListener listener = new IGazeListener () {
        	@Override
            public void onGazeUpdate(GazeData gazeData)
            {
        		/*
        		 * // cursor.setLocation(gazeData.smoothedCoordinates.x - this.getGCanvas().getLocationOnScreen().x, gazeData.smoothedCoordinates.y - this.getGCanvas().getLocationOnScreen().y);
 -        		if (gazeData.smoothedCoordinates.x - this.getGCanvas().getLocationOnScreen().x < TrackerConstants.SCREEN_DIVISION_X) leftCount++;
 -        		totalTimeSteps++;
        		
        		currentGazeDataSet.add(new EyeEntry(gazeData, isOnTrackerScreen()));
        	
            }
        };
        gm.addGazeListener(listener);*/
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                /*gm.removeGazeListener(listener);
                gm.deactivate();*/
                entries.closeAll();
            }
        });
		startTime = System.currentTimeMillis();	
		Tuple move;
		boolean initialized = false;
		boolean pressedButton = false;
		double lastPressed = System.currentTimeMillis();
		TrackerEntry te;
		double loopTime;
		double lastTrackerCheck = System.currentTimeMillis();
		while (true) {
			System.out.print(running?"":""); //it is unclear why this is required, but something needs to check the running variable
			if (audio.playCompleted) audio.close();
			if (running) {
				if (!initialized) {
					initialized = true;
					addMouseListeners();
					addKeyListeners();
				}				
				loopTime = System.currentTimeMillis();
				/*if (System.currentTimeMillis() % 1000 == 0)
					seed = Math.random();*/
				if (loopTime % 30 == 0/* && loopTime > startTime + 400*/) {
					joystick.poll();
					if (!pressedButton) {
						if (joystick.getComponents()[0].getPollData() > 0.5) { //trigger
							audio.play("sounds/ack.wav");
							audio = new AudioPlayer();
							pressedButton = true;
							preEnteredAnswer = 0;
							timeSpent = System.currentTimeMillis() - startTime;
						}
						else if (joystick.getComponents()[1].getPollData() > 0.5) { //button on the thumb
							audio.play("sounds/ack.wav");
							audio = new AudioPlayer();
							pressedButton = true;
							preEnteredAnswer = 1;
							timeSpent = System.currentTimeMillis() - startTime;
						}
					}
					if (joystick.getComponents()[2].getPollData() > 0.5 && System.currentTimeMillis() - lastPressed > 500) { 
						lastPressed = System.currentTimeMillis(); //refractory period so holding down the toggle doesn't continually switch back and forth
						toggles++;
						if (this.isOnTrackerScreen()) {
							this.changeScreenLeft();
						}
						else {
							this.changeScreenRight();
						}
						if (!inPracticeMode) entries.addToggle(this.startTime, this.counter, this.isOnTrackerScreen());
					}
					moveJoystick(joystick.getComponents()[12].getPollData(), joystick.getComponents()[13].getPollData());
					move = p.computeMove();
					moveCursorSwarm(move.x, -1 * move.y); //just reverse it here
					if (p.cursor.distance(new Tuple(0, 0)) < TrackerConstants.TARGET_SIZE) 
						inCircleSteps++;
					totalTimeSteps++;
					/*currentTime = System.currentTimeMillis() / 200d / Math.PI;
					cursor.movePolar((Math.sin(currentTime) - Math.sin(lastTime)) * 40, lastAngle);
					lastTime = System.currentTimeMillis() / 200d / Math.PI;
					lastAngle += seed / 1500d;*/
					//if (cursor.getxX() + cursor.getWidth() < TrackerConstants.SCREEN_DIVISION_X && cursor.isVisible()) cursor.setVisible(false);
				}	
				if (loopTime % 100 == 0 && counter >= 1 && loopTime - lastTrackerCheck > 50) {
					lastTrackerCheck = System.currentTimeMillis();
					te = new TrackerEntry(counter, p.cursor, p.mouseDiff, this.isOnTrackerScreen());
					entries.addTrackerEntry(te); //seldom do this
					this.totalTrackerChecks++;
					if (this.isOnTrackerScreen()) this.onTrackerChecks++;
				}
				if (loopTime % 10 == 0/* && loopTime > startTime + 1200*/) {
					if (timer == null) {
						timer = new GLabel("0");
						timer.setColor(Color.WHITE);
						timer.setFont(new Font("Arial", Font.BOLD, 14));
						timer.setLocation(TrackerConstants.TIMER_Y, TrackerConstants.TIMER_Y);
						tList.add(timer);
						pList.add(timer);
						this.add(timer);
					}
					timer.setLabel("Time left: " + Double.toString((int)(100 * (TrackerConstants.TRIAL_LENGTH_MS - (System.currentTimeMillis() - startTime - pauseBank)) / 1000d) / 100d));
					if (TrackerConstants.TRIAL_LENGTH_MS - (System.currentTimeMillis() - startTime - pauseBank) <= 0) {
						p = new Physics();
						pressedButton = false;
						nextRound();
						resetCursorSwarm();
						this.changeScreenRight();
					}
				}
			}
		}
	}
}

