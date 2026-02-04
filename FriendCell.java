package client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

public class FriendCell extends JPanel implements ListCellRenderer<String>{
	private final JLabel nameLabel = new JLabel();
    private final Map<String, String> statusMap;
    private final Set<String> onlineSet;
    private String currentStatus = "offline";
    
    public FriendCell(Map<String, String> statusMap, Set<String> onlineSet) {
        this.statusMap = statusMap;
        this.onlineSet = onlineSet;

        setOpaque(true);
        setLayout(new BorderLayout(10, 0));
        setBorder(BorderFactory.createEmptyBorder(6, 22, 6, 10));

        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        add(nameLabel, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends String> list, String friend, int index, boolean isSelected, boolean cellHasFocus) {
    	String sta = statusMap.get(friend);
    	if(sta == null) {
    		sta = "offline";
    		if (onlineSet.contains(friend)) {
    		    sta = "online";
    		}
    	}
    	currentStatus = sta;
    	nameLabel.setText(friend);
    	if(isSelected) {
    		setBackground(new Color(220, 240, 255));
    	} else {
    		setBackground(Color.WHITE);
    	}
    	return this;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
    	super.paintComponent(g);
    	Graphics2D g2D = (Graphics2D) g.create();
    	g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    	Color dotColor;
    	if ("online".equals(currentStatus)) {
    	    dotColor = new Color(0, 170, 0);
    	} else if ("away".equals(currentStatus)) {
    	    dotColor = new Color(255, 180, 0);
    	} else if ("busy".equals(currentStatus)) {
    	    dotColor = new Color(220, 0, 0);
    	} else {
    	    dotColor = new Color(170, 170, 170);
    	}
    	
    	g2D.setColor(dotColor);
    	g2D.fillOval(10, (getHeight() - 10) / 2, 10, 10);
    	g2D.setColor(Color.DARK_GRAY);
    	g2D.drawOval(10, (getHeight() - 10) / 2, 10, 10);
    	g2D.dispose();
    }
}
