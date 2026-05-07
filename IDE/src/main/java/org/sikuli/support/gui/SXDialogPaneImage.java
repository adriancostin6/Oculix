package org.sikuli.support.gui;

import org.apache.commons.io.FilenameUtils;
import org.sikuli.ide.EditorImageButton;
import org.sikuli.ide.SikulixIDE;
import org.sikuli.script.Region;
import org.sikuli.script.SX;
import org.sikuli.support.RunTime;
import org.sikuli.support.devices.ScreenDevice;

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
    // Resolve the screen hosting the IDE window with progressive fallback:
    //  1. center point — robust if the window has been dragged partially off-screen
    //  2. top-left corner — original behaviour, kept for compat
    //  3. primary screen — last resort instead of a fatal terminate
    final Point center = new Point(
        ideWindow.x + ideWindow.width / 2,
        ideWindow.y + ideWindow.height / 2);
    ScreenDevice scr = ScreenDevice.getScreenDeviceForPoint(center);
    if (scr == null) {
      scr = ScreenDevice.getScreenDeviceForPoint(ideWindow.getLocation());
    }
    if (scr == null) {
      scr = ScreenDevice.primary();
    }
    if (scr == null) {
      RunTime.terminate(999, "SXDialogPaneImage: prepare(): no screen available for IDE window");
    }
    SikulixIDE.doHide();
    scrImage = scr.capture();
    globalStore.put("screenshot", scrImage);
    actualImage = adjustTo(ideWindow, scrImage);
  }

  public void rename() {
    closeCancel();
    final String image = FilenameUtils.getBaseName(((File) getOptions().get("image")).getAbsolutePath());
    final Region showAt = new Region(getLocation().x, getLocation().y, 1, 1);
    final String name = SX.input("New name for image " + image, "ImageButton :: rename", showAt);
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
