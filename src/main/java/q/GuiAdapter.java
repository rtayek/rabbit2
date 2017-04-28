package q;
import java.util.*;
import p.Main;
import static p.IO.*;
public interface GuiAdapter extends Observer {
    // maybe it's time to toss this
    // as we are not using it in cb2.
	void setButtonState(int id,boolean state); // of the widget!
	void setButtonText(int id,String string); // of the widget!
	public abstract class GuiAdapterABC implements GuiAdapter {
		public GuiAdapterABC(Main main) {
			this.main=main;
		}
		public void processClick(int index) {
			int id=index+1;
			if(1<=id&&id<=main.model.buttons) main.instance().click(id);
			else p(id+" is bad button id!");
		}
		@Override public void update(Observable observable,Object hint) {
			for(Integer buttonId=1;buttonId<=main.model.buttons;buttonId++) {
				setButtonState(buttonId,main.model.state(buttonId));
				setButtonText(buttonId,"id");
			}
		}
		final Main main;
	}
}
