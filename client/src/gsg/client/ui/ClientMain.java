package gsg.client.ui;

import javax.swing.SwingUtilities;

public class ClientMain {
	public static void main(String[] args) {
		 SwingUtilities.invokeLater(new Runnable() {
	            public void run() {
	                new MainFrame().setVisible(true);
	            }
	        });
	}
}
