package junit.awtui;

import junit.framework.*;
import junit.runner.*;

import java.util.Vector;
import java.lang.reflect.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
 
/**
 * An AWT based user interface to run tests.
 * Enter the name of a class which either provides a static
 * suite method or is a subclass of TestCase.
 * <pre>
 * Synopsis: java junit.awtui.TestRunner [-noloading] [TestCase]
 * </pre>
 * TestRunner takes as an optional argument the name of the testcase class to be run.
 */
 public class TestRunner extends BaseTestRunner {
	protected Frame fFrame;
	protected Vector fExceptions;
	protected Vector fFailedTests;
	protected Thread fRunner;
	protected TestResult fTestResult;
	
	protected TextArea fTraceArea;
	protected TextField fSuiteField;
	protected Button fRun;
	protected ProgressBar fProgressIndicator;
	protected List fFailureList;
	protected Logo fLogo;
	protected Label fNumberOfErrors;
	protected Label fNumberOfFailures;
	protected Label fNumberOfRuns;
	protected Button fQuitButton;
	protected Button fRerunButton;
	protected TextField fStatusLine;
	
	protected static Font PLAIN_FONT= new Font("dialog", Font.PLAIN, 12);
	private static final int GAP= 4;
	private static final String SUITE_METHODNAME= "suite";
	
	public TestRunner() {
	}
	 
	private void about() {
		AboutDialog about= new AboutDialog(fFrame);
		about.setModal(true);
		about.setLocation(300, 300);
		about.setVisible(true);
	}
	
	public void addError(Test test, Throwable t) {
		fNumberOfErrors.setText(Integer.toString(fTestResult.errorCount()));
		appendFailure("Error", test, t);
	}

	public void addFailure(Test test, AssertionFailedError t) {
		fNumberOfFailures.setText(Integer.toString(fTestResult.failureCount()));
		appendFailure("Failure", test, t);
	}
	
	protected void addGrid(Panel p, Component co, int x, int y, int w, int fill, double wx, int anchor) {
		GridBagConstraints c= new GridBagConstraints();
		c.gridx= x; c.gridy= y;
		c.gridwidth= w;
		c.anchor= anchor;
		c.weightx= wx;
		c.fill= fill;
		if (fill == GridBagConstraints.BOTH || fill == GridBagConstraints.VERTICAL)
			c.weighty= 1.0;
		c.insets= new Insets(y == 0 ? GAP : 0, x == 0 ? GAP : 0, GAP, GAP);
		p.add(co, c);
	}
	
	private void appendFailure(String kind, Test test, Throwable t) {
		kind+= ": " + test;
		String msg= t.getMessage();
		if (msg != null) {
			kind+= ":" + truncate(msg); 
		}
		fFailureList.add(kind);
		fExceptions.addElement(t);
		fFailedTests.addElement(test);
		if (fFailureList.getItemCount() == 1) {
			fFailureList.select(0);
			failureSelected();	
		}
	}
	/**
	 * Creates the JUnit menu. Clients override this
	 * method to add additional menu items.
	 */
	protected Menu createJUnitMenu() {
		Menu menu= new Menu("JUnit");
		MenuItem mi= new MenuItem("About...");
		mi.addActionListener(
		    new ActionListener() {
		        public void actionPerformed(ActionEvent event) {
		            about();
		        }
		    }
		);
		menu.add(mi);
		
		menu.addSeparator();
		mi= new MenuItem("Exit");
		mi.addActionListener(
		    new ActionListener() {
		        public void actionPerformed(ActionEvent event) {
		            System.exit(0);
		        }
		    }
		);
		menu.add(mi);
		return menu;
	}
	
	protected void createMenus(MenuBar mb) {
		mb.add(createJUnitMenu());
	}
	protected TestResult createTestResult() {
		return new TestResult();
	}
	
	protected Frame createUI(String suiteName) {	
		Frame frame= new Frame("JUnit");
		Image icon= loadFrameIcon();	
		if (icon != null)
			frame.setIconImage(icon);

		frame.setLayout(new BorderLayout(0, 0));
		frame.setBackground(SystemColor.control);
		final Frame finalFrame= frame;
		
		frame.addWindowListener(
			new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					finalFrame.dispose();
					System.exit(0);
				}
			}
		); 

		MenuBar mb = new MenuBar();
		createMenus(mb);
		frame.setMenuBar(mb);
		
		//---- first section
		Label suiteLabel= new Label("Enter the name of the Test class:");

		fSuiteField= new TextField(suiteName != null ? suiteName : "");
		fSuiteField.selectAll();
		fSuiteField.requestFocus();
		fSuiteField.setFont(PLAIN_FONT);
		fSuiteField.setColumns(40);
		fSuiteField.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					runSuite();
				}
			}
		);
		fSuiteField.addTextListener(
			new TextListener() {
				public void textValueChanged(TextEvent e) {
					fRun.setEnabled(fSuiteField.getText().length() > 0);
					fStatusLine.setText("");
				}
			}
		);
		fRun= new Button("Run");
		fRun.setEnabled(false);
		fRun.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					runSuite();
				}
			}
		);
	
		//---- second section
		fProgressIndicator= new ProgressBar();	

		//---- third section
		fNumberOfErrors= new Label("0000", Label.RIGHT);
		fNumberOfErrors.setText("0");
		fNumberOfErrors.setFont(PLAIN_FONT);
	
		fNumberOfFailures= new Label("0000", Label.RIGHT);
		fNumberOfFailures.setText("0");
		fNumberOfFailures.setFont(PLAIN_FONT);
	
		fNumberOfRuns= new Label("0000", Label.RIGHT);
		fNumberOfRuns.setText("0");
		fNumberOfRuns.setFont(PLAIN_FONT);
	
		Panel numbersPanel= new Panel(new FlowLayout());
		numbersPanel.add(new Label("Runs:"));			numbersPanel.add(fNumberOfRuns);
		numbersPanel.add(new Label("   Errors:"));		numbersPanel.add(fNumberOfErrors);
		numbersPanel.add(new Label("   Failures:"));	numbersPanel.add(fNumberOfFailures);

	
		//---- fourth section
		Label failureLabel= new Label("Errors and Failures:");
		
		fFailureList= new List(5);
		fFailureList.addItemListener(
			new ItemListener() {
				public void itemStateChanged(ItemEvent e) {
					failureSelected();
				}
			}
		);
		fRerunButton= new Button("Run");
		fRerunButton.setEnabled(false);
		fRerunButton.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					rerun();
				}
			}
		);

		Panel failedPanel= new Panel(new GridLayout(0, 1, 0, 2));
		failedPanel.add(fRerunButton);
		
		fTraceArea= new TextArea();
		fTraceArea.setRows(5);
		fTraceArea.setColumns(60);

		//---- fifth section
		fStatusLine= new TextField();
		fStatusLine.setFont(PLAIN_FONT);
		fStatusLine.setEditable(false);
		fStatusLine.setForeground(Color.red);

		fQuitButton= new Button("Exit");
		fQuitButton.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					System.exit(0);
				}
			}
		);
	
		// ---------
		fLogo= new Logo();
	
		//---- overall layout
		Panel panel= new Panel(new GridBagLayout());
	
		addGrid(panel, suiteLabel,		 0, 0, 2, GridBagConstraints.HORIZONTAL, 	1.0, GridBagConstraints.WEST);
		
		addGrid(panel, fSuiteField, 	 0, 1, 2, GridBagConstraints.HORIZONTAL, 	1.0, GridBagConstraints.WEST);
		addGrid(panel, fRun, 			 2, 1, 1, GridBagConstraints.HORIZONTAL, 	0.0, GridBagConstraints.CENTER);

		addGrid(panel, fProgressIndicator, 0, 3, 2, GridBagConstraints.HORIZONTAL, 	1.0, GridBagConstraints.WEST);
		addGrid(panel, fLogo, 			 2, 3, 1, GridBagConstraints.NONE, 			0.0, GridBagConstraints.NORTH);

		addGrid(panel, numbersPanel,	 0, 4, 2, GridBagConstraints.NONE, 			0.0, GridBagConstraints.CENTER);

		addGrid(panel, failureLabel, 	 0, 5, 2, GridBagConstraints.HORIZONTAL, 	1.0, GridBagConstraints.WEST);
		addGrid(panel, fFailureList, 	 0, 6, 2, GridBagConstraints.BOTH, 			1.0, GridBagConstraints.WEST);
		addGrid(panel, failedPanel, 	 2, 6, 1, GridBagConstraints.HORIZONTAL, 	0.0, GridBagConstraints.CENTER);
		addGrid(panel, fTraceArea, 	     0, 7, 2, GridBagConstraints.BOTH, 			1.0, GridBagConstraints.WEST);
		
		addGrid(panel, fStatusLine, 	 0, 8, 2, GridBagConstraints.HORIZONTAL, 	1.0, GridBagConstraints.CENTER);
		addGrid(panel, fQuitButton, 	 2, 8, 1, GridBagConstraints.HORIZONTAL, 	0.0, GridBagConstraints.CENTER);
		
		frame.add(panel, BorderLayout.CENTER);
		frame.pack();
		return frame;
	}
	
	public void failureSelected() {
		fRerunButton.setEnabled(isErrorSelected());
		showErrorTrace();
	}

	public void endTest(Test test) {
		setLabelValue(fNumberOfRuns, fTestResult.runCount());
		synchronized(this) {
			fProgressIndicator.step(fTestResult.wasSuccessful());
		}
	}
		
	private boolean isErrorSelected() {
		return fFailureList.getSelectedIndex() != -1;
	}
	
	private Image loadFrameIcon() {
		Toolkit toolkit= Toolkit.getDefaultToolkit();
		try {
			java.net.URL url= BaseTestRunner.class.getResource("smalllogo.gif");
			return toolkit.createImage((ImageProducer) url.getContent());
		} catch (Exception ex) {
		}
		return null;
	}
	
	public Thread getRunner() {
		return fRunner;
	}
	
	public static void main(String[] args) {
		new TestRunner().start(args);
	}
	 
	public static void run(Class test) {
		String args[]= { test.getName() };	
		main(args);
	}
	
	public void rerun() {
		int index= fFailureList.getSelectedIndex();
		if (index == -1)
			return;
	
		Test test= (Test)fFailedTests.elementAt(index);
		if (!(test instanceof TestCase)) {
			showInfo("Could not reload "+ test.toString());
			return;
		}
		Test reloadedTest= null;
		try {
			Class reloadedTestClass= getLoader().reload(test.getClass());
			Class[] classArgs= { String.class };
			Constructor constructor= reloadedTestClass.getConstructor(classArgs);
			Object[] args= new Object[]{((TestCase)test).name()};
			reloadedTest=(Test)constructor.newInstance(args);
		} catch(Exception e) {
			showInfo("Could not reload "+ test.toString());
			return;
		}
		TestResult result= new TestResult();
		reloadedTest.run(result);
		
		String message= reloadedTest.toString();
		if(result.wasSuccessful())
			showInfo(message+" was successful");
		else if (result.errorCount() == 1)
			showStatus(message+" had an error");
		else
			showStatus(message+" had a failure");
	}
	
	protected void reset() {
		setLabelValue(fNumberOfErrors, 0);
		setLabelValue(fNumberOfFailures, 0);
		setLabelValue(fNumberOfRuns, 0);
		fProgressIndicator.reset();
		fRerunButton.setEnabled(false);
		fFailureList.removeAll();
		fExceptions= new Vector(10);
		fFailedTests= new Vector(10);
		fTraceArea.setText("");

	}
	/**
	 * runs a suite.
	 * @deprecated use runSuite() instead
	 */
	public void run() {
		runSuite();
	}
	
	protected void runFailed(String message) {
		showStatus(message);
		fRun.setLabel("Run");
		fRunner= null;
	}
	
	synchronized public void runSuite() {
		if (fRunner != null) {
			fTestResult.stop();
		} else {
			fRun.setLabel("Stop");
			showInfo("Initializing...");
			reset();
			
			showInfo("Load Test Case...");
			final Test testSuite= getTest(fSuiteField.getText());
			if (testSuite != null) {
				fRunner= new Thread() {
					public void run() {
						fTestResult= createTestResult();
						fTestResult.addListener(TestRunner.this);
						fProgressIndicator.start(testSuite.countTestCases());
						showInfo("Running...");
					
						long startTime= System.currentTimeMillis();
						testSuite.run(fTestResult);
						
						if (fTestResult.shouldStop()) {
							showStatus("Stopped");
						} else {
							long endTime= System.currentTimeMillis();
							long runTime= endTime-startTime;
							showInfo("Finished: " + elapsedTimeAsString(runTime) + " seconds");
						}
						fTestResult= null;
						fRun.setLabel("Run");
						fRunner= null;
						System.gc();
					}
				};
				fRunner.start();
			}
		}
	}
	
	private void setLabelValue(Label label, int value) {
		label.setText(Integer.toString(value));
	}
	
	public void setSuiteName(String suite) {
		fSuiteField.setText(suite);
	}
	
	private void showErrorTrace() {
		int index= fFailureList.getSelectedIndex();
		if (index == -1)
			return;
	
		Throwable t= (Throwable) fExceptions.elementAt(index);
		fTraceArea.setText(filterStack(getTrace(t)));
	}
	
	private String getTrace(Throwable t) { 
		StringWriter stringWriter= new StringWriter();
		PrintWriter writer= new PrintWriter(stringWriter);
		t.printStackTrace(writer);
		StringBuffer buffer= stringWriter.getBuffer();
		return buffer.toString();
	}

	private void showInfo(String message) {
		fStatusLine.setFont(PLAIN_FONT);
		fStatusLine.setForeground(Color.black);
		fStatusLine.setText(message);
	}
	
	protected void clearStatus() {
		showStatus("");
	}

	private void showStatus(String status) {
		fStatusLine.setFont(PLAIN_FONT);
		fStatusLine.setForeground(Color.red);
		fStatusLine.setText(status);
	}
	/**
	 * Starts the TestRunner
	 */
	public void start(String[] args) {
		String suiteName= processArguments(args);			
		fFrame= createUI(suiteName);
		fFrame.setLocation(200, 200);
		fFrame.setVisible(true);
	
		if (suiteName != null) {
			setSuiteName(suiteName);
			runSuite();
		}
	}
	
	public void startTest(Test test) {
		showInfo("Running: "+test);
	}
}