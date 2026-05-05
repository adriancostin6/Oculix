package org.sikuli.support.gui;

import org.apache.commons.io.FilenameUtils;
import org.sikuli.ide.EditorImageButton;
import org.sikuli.ide.SikulixIDE;
import org.sikuli.support.RunTime;
import org.sikuli.support.devices.ScreenDevice;

import javax.swing.JOptionPane;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class SXDialogPaneImage extends SXDialogIDE {
  public SXDialogPaneImage(Point where, Object... parms) {
    super("sxidepaneimage", where, parms);
  }

  public SXDialogPaneImage(String res, Point where, Object... parms) {
    super(res, where, parms);
  }

  File image = (File) getOptions().get("image");
  BufferedImage actualImage = null;
  protected BufferedImage scrImage = null;
  Rectangle ideWindow = null;

  private void prepare() {
    ideWindow = getIdeWindow();
    final ScreenDevice scr = ScreenDevice.getScreenDeviceForPoint(ideWindow.getLocation());
    if (scr == null) {
      RunTime.terminate(999, "SXDialogPaneImage: prepare(): ideWindow.getLocation(): should be on a valid screen");
    }
    SikulixIDE.doHide();
    scrImage = scr.capture();
    globalStore.put("screenshot", scrImage);
    actualImage = adjustTo(ideWindow, scrImage);
  }

  public void rename() {
    closeCancel();
    final String image = FilenameUtils.getBaseName(((File) getOptions().get("image")).getAbsolutePath());
    // Switched from SX.input(msg, title, region) to a direct
    // JOptionPane.showInputDialog. SX.input's third arg slot expects a String
    // preset (cf. SX.getPopParameters parameterClass = "s,s,s,b,i,e,o") but
    // SXDialogPaneImage was passing a Region. Parameter parsing silently
    // returned null even when the user typed text and clicked OK — so the
    // entire ImageButton rename action was a no-op for everyone, regardless
    // of input. The direct JOptionPane call also gives us the current image
    // name as a sane preset so a one-character correction is one keystroke
    // away.
    final String name = JOptionPane.showInputDialog(
        SikulixIDE.get(),
        "New name for image " + image,
        image);
    EditorImageButton.renameImage(name, getOptions());
  }

  public void optimize() {
    closeCancel();
    prepare();
    final SXDialogPaneImageOptimize dlgOptimize = new SXDialogPaneImageOptimize(ideWindow.getLocation(),
        new String[]{"image", "shot"}, image, actualImage, this);
    dlgOptimize.setText("statusline", "searching... +");
    dlgOptimize.run();
  }

  public void pattern() {
    closeCancel();
    prepare();
    new SXDialogPaneImagePattern(ideWindow.getLocation(), new String[]{"image", "shot", "pattern"},
        getOptions().get("image"), actualImage).run();
  }
}
