/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.mmutil;

import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.mmutil.MantaMatcherScan;


import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.MantaMatcher;
import org.ecocean.servlet.ServletUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jdo.Extent;
import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author Giles Winstanley
 */
public final class MantaMatcherUtilities {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(MantaMatcherUtilities.class);

  private MantaMatcherUtilities() {}

  /**
   * Converts the specified file to a map of files, keyed by string for easy
   * referencing, comprising the following elements:
   * <ul>
   * <li>Original file (key: O).</li>
   * <li>File representing MantaMatcher &quot;candidate region&quot; photo (key: CR).</li>
   * <li>File representing MantaMatcher enhanced photo (key: EH).</li>
   * <li>File representing MantaMatcher feature photo (key: FT).</li>
   * <li>File representing MantaMatcher feature file (key: FEAT).</li>
   * <li>File representing MantaMatcher algorithm scan data file (key: MMA-SCAN-DATA).</li>
   * </ul>
   * All files are assumed to be in the same folder, and no checking is
   * performed to see if they exist.
   * @param spv {@code SinglePhotoVideo} instance denoting base reference image
   * @return Map of string to file for each MantaMatcher algorithm feature.
   */
  public static Map<String, File> getMatcherFilesMap(MediaAsset ma) {
    if (ma == null)
      throw new NullPointerException("Invalid media asset specified: null");
    //String maURL=enc.subdir()+File.separator+ma.getFilename();
    File file=ma.localPath().toFile();
    System.out.println("...Trying to load matcher files from: "+ma.localPath().toFile().getAbsolutePath());
    Map<String, File> map = getMatcherFilesMap(file);
    map.put("MMA-SCAN-DATA", new File(file.getParentFile(), String.format("%s_mmaScanData.dat", ma.getId())));
    return map;
  }

  /**
   * Converts the specified file to a map of files, keyed by string for easy
   * referencing, comprising the following elements:
   * <ul>
   * <li>Original file (key: O).</li>
   * <li>File representing MantaMatcher &quot;candidate region&quot; photo (key: CR).</li>
   * <li>File representing MantaMatcher enhanced photo (key: EH).</li>
   * <li>File representing MantaMatcher feature photo (key: FT).</li>
   * <li>File representing MantaMatcher feature file (key: FEAT).</li>
   * </ul>
   * All files are assumed to be in the same folder, and if existing
   * web-compatible image files exist (based on file extension) they will be
   * referenced, otherwise a generic name is used (with the same extension as
   * the original file).
   * <p>
   * This allows simple determination of, for example, existence of a CR file
   * for a specified original:
   * <pre>
   * Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
   * if (mmFiles.get("CR").exists()) {
   *     // ...do something ...
   * }
   * </pre>
   *
   * The functionality is centralized here to reduce naming errors/conflicts.
   * @param f base image file from which to reference other algorithm files
   * @return Map of string to file for each MantaMatcher algorithm feature.
   */
  public static Map<String, File> getMatcherFilesMap(File f) {
    if (f == null)
      throw new NullPointerException("Invalid file specified: null");
    String name = f.getName();
    String regFormat = MediaUtilities.REGEX_SUFFIX_FOR_WEB_IMAGES;
    if (!name.matches("^.+\\." + regFormat)){
      System.out.println("...Hey, that's not a valid file format!");
      throw new IllegalArgumentException("Invalid file type specified: " + f.getName());
    }  
    String regex = "\\." + regFormat;
    File pf = f.getParentFile();

    // Locate web-compatible image files for each type (if possible).
    // (This provides basic support if in future mmprocess allows original/CR
    // files to have different file extensions.)
    String baseName = name.replaceFirst(regex, "");
    List<File> crOpts = MediaUtilities.listWebImageFiles(pf, baseName + "_CR");
    List<File> ehOpts = MediaUtilities.listWebImageFiles(pf, baseName + "_EH");
    List<File> ftOpts = MediaUtilities.listWebImageFiles(pf, baseName + "_FT");

    // Use existing image files if possible, otherwise use generic name with same file extension.
    File cr = crOpts.isEmpty() ? new File(pf, name.replaceFirst(regex, "_CR.$1")) : crOpts.get(0);
    String crExt = cr.getName().substring(cr.getName().lastIndexOf(".") + 1);
    File eh = ehOpts.isEmpty() ? new File(pf, name.replaceFirst(regex, "_EH." + crExt)) : ehOpts.get(0);
    File ft = ftOpts.isEmpty() ? new File(pf, name.replaceFirst(regex, "_FT." + crExt)) : ftOpts.get(0);
    File feat = new File(pf, name.replaceFirst(regex, ".FEAT"));

    Map<String, File> map = new HashMap<String, File>(11);
    map.put("O", f);
    map.put("CR", cr);
    map.put("EH", eh);
    map.put("FT", ft);
    map.put("FEAT", feat);
    System.out.println("...returning matcher filesmap of size: "+map.size());
    return map;
  }

  /**
   * Checks whether the pre-processed MantaMatcher algorithm files exist for
   * the specified base image file (only checks the files required for running
   * {@code mmatch}).
   * @param f base image file from which to reference other algorithm files
   * @return true if all MantaMatcher files exist (O/CR/EH/FT/FEAT), false otherwise
   */
  public static boolean checkMatcherFilesExist(File f) {
    Map<String, File> mmFiles = getMatcherFilesMap(f);
    return mmFiles.get("O").exists() &&
            mmFiles.get("CR").exists() &&
            mmFiles.get("EH").exists() &&
            mmFiles.get("FT").exists();
  }

  /**
   * Checks whether the pre-processed MantaMatcher algorithm files exist for
   * the specified base image file (only checks the files required for running
   * {@code mmatch}), and that they are usable.
   * Compared to {@link #checkMatcherFilesExist(File)} this method also
   * checks that the {@code .FEAT} file exists, which contains the usable features for matching.
   * Separating these methods allows simple checking of failed feature extraction.
   * @param f base image file from which to reference other algorithm files
   * @return true if all MantaMatcher files exist (O/CR/EH/FT/FEAT), false otherwise
   */
  public static boolean checkMatcherFilesUsable(File f) {
    Map<String, File> mmFiles = getMatcherFilesMap(f);
    return mmFiles.get("O").exists() &&
            mmFiles.get("CR").exists() &&
            mmFiles.get("EH").exists() &&
            mmFiles.get("FT").exists() &&
            mmFiles.get("FEAT").exists();
  }

  /**
   * Checks whether the pre-processed MantaMatcher algorithm files exist for
   * an encounter (which may be used to determine MMA-compatibility status).
   * @param enc encounter to test
   * @param dataDir data folder
   * @return true if any CR images are found, false otherwise
   */
  public static boolean checkEncounterHasMatcherFiles(Encounter enc, File dataDir) {
    for (MediaAsset ma : enc.getMedia()) {
      File file=ma.localPath().toFile();
      if (MediaUtilities.isAcceptableImageFile(file) && checkMatcherFilesExist(file)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Removes all MantaMatcher algorithm files relating to the specified
   * base image file.
   * @param context webapp context for data reference
   * @param spv {@code SinglePhotoVideo} instance denoting base reference image
   */
  public static void removeMatcherFiles(String context, MediaAsset ma, Encounter enc) throws IOException {
    removeAlgorithmMatchResults(context, ma,enc);
    Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(ma);
    mmFiles.get("CR").delete();
    mmFiles.get("EH").delete();
    mmFiles.get("FT").delete();
    mmFiles.get("FEAT").delete();
  }

  /**
   * Collates text input for the MantaMatcher algorithm.
   * The output of this method is suitable for placing in a temporary file
   * to be access by the {@code mmatch} process.
   * @param shep {@code Shepherd} instance
   * @param encDir &quot;encounters&quot; directory
   * @param enc {@code Encounter} instance
   * @param spv {@code SinglePhotoVideo} instance
   * @param locationIDs collection of LocationIDs to use for algorithm input
   * @return text suitable for MantaMatcher algorithm input file
   */
  @SuppressWarnings("unchecked")
  public static String collateAlgorithmInput(Shepherd shep, Encounter enc, MediaAsset ma, Collection<String> locationIDs, String dataDir) {
    // Validate input.
    Objects.requireNonNull(locationIDs);
    if (enc.getLocationID() == null)
      throw new IllegalArgumentException("Invalid location ID specified");
    //if (encDir == null || !encDir.isDirectory())
      //throw new IllegalArgumentException("Invalid encounter directory specified");
    if (enc == null || ma == null)
      throw new IllegalArgumentException("Invalid encounter/SPV specified");

    // Build query filter based on encounter.
    StringBuilder sbf = new StringBuilder();
    sbf.append("(").append(org.ecocean.StringUtils.collateStrings(locationIDs, "this.locationID == '", "'", " || ")).append(")");
    if (enc.getSpecificEpithet()!= null) {
      if (sbf.length() > 0)
        sbf.append(" && ");
      sbf.append("(this.specificEpithet == null");
      sbf.append(" || this.specificEpithet == '").append(enc.getSpecificEpithet()).append("'");
      sbf.append(")");
    }
    if (enc.getPatterningCode() != null) {
      if (sbf.length() > 0)
        sbf.append(" && ");
      sbf.append("(this.patterningCode == null");
      // Normal & White mantas should always be compared.
      if (enc.getPatterningCode().matches("^(normal|white).*$"))
        sbf.append(" || this.patterningCode.startsWith('normal') || this.patterningCode.startsWith('white')");
      else
        sbf.append(" || this.patterningCode == '").append(enc.getPatterningCode()).append("'");
      sbf.append(")");
    }
    if (enc.getSex() != null && !"unknown".equals(enc.getSex())) {
      if (sbf.length() > 0)
        sbf.append(" && ");
      sbf.append("(this.sex == null");
      sbf.append(" || this.sex == 'unknown'");
      sbf.append(" || this.sex == '").append(enc.getSex()).append("'");
      sbf.append(")");
    }
//    log.trace(String.format("Filter: %s", sbf.toString()));

    // Issue query.
    Extent ext = shep.getPM().getExtent(Encounter.class, true);
    Query query = shep.getPM().newQuery(ext);
    query.setFilter(sbf.toString());
    List<Encounter> list = (List<Encounter>)query.execute();

    // Collate results.
    StringBuilder sb = new StringBuilder();
//    sb.append(spv.getFile().getParent()).append("\n\n");
    File file=ma.localPath().toFile();
    sb.append(file.getAbsolutePath()).append("\n\n");
    
    for (Encounter x : list) {
      if (!enc.getEncounterNumber().equals(x.getEncounterNumber())){
        //sb.append(encDir.getAbsolutePath()).append(File.separatorChar).append(x.subdir()).append("\n");
        sb.append(x.dir(dataDir)).append("\n");
        sb.append(x.dir(dataDir).replaceAll("/encounters", "")).append("\n");
      }
    }

    // Clean resources.
    query.closeAll();
    ext.closeAll();

    return sb.toString();
  }

  /**
   * Removes any previously generated algorithm match results for the
   * specified encounter.
   * It's recommended this method be called whenever any match critical data
   * fields are changed (e.g. species/patterningCode/locationID).
   * @param context webapp context for data reference
   * @param spv SinglePhotoVideo for which to remove algorithm match results
   */
  public static void removeAlgorithmMatchResults(String context, MediaAsset ma, Encounter enc) {
    Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(ma);
    File f = mmFiles.get("MMA-SCAN-DATA");
    if (f.exists())
      f.delete();
  }

  /**
   * Returns the MMA scans for the specified SinglePhotoVideo with the specified ID, or null if not found.
   * @param context webapp context for data reference
   * @param spv SinglePhotoVideo instance for which to retrieve scans
   * @return ID of scan (currently based on millisecond date/time)
   */
  public static MantaMatcherScan findMantaMatcherScan(String context, MediaAsset ma, String id) throws IOException {
    Objects.requireNonNull(context);
    Objects.requireNonNull(ma);
    Objects.requireNonNull(id);
    for (MantaMatcherScan scan : loadMantaMatcherScans(context, ma)) {
      if (id.equals(scan.getId()))
        return scan;
    }
    return null;
  }

  /**
   * Returns the set of existing MMA scans for the specified SinglePhotoVideo.
   * @param context webapp context for data reference
   * @param spv SinglePhotoVideo instance for which to retrieve scans
   * @return set of MantaMatcherScan instances
   */
  public static Set<MantaMatcherScan> loadMantaMatcherScans(String context, MediaAsset ma) throws IOException {
    Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(ma);
    File f = mmFiles.get("MMA-SCAN-DATA");
    if (!f.exists()){
      System.out.println("...I can't find MMA-SCAN-DATA in loadMantaMatcherScans");
      return new TreeSet<>();
    }
    try (ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(new FileInputStream(f)))) {
      Set<MantaMatcherScan> scans = (Set<MantaMatcherScan>)in.readObject();
      return scans;
    }
    catch (FileNotFoundException e) {
      return Collections.EMPTY_SET;
    }
    catch (Exception ex) {
      log.warn("Failed to load MantaMatcher scans for MediaAsset: " + ma.getId(), ex);
      if (ex instanceof IOException)
        throw (IOException)ex;
      throw new IOException(ex);
    }
  }

  /**
   * Returns the list of existing MMA scans for the specified SinglePhotoVideo.
   * @param context webapp context for data reference
   * @param spv SinglePhotoVideo instance for which to retrieve scans
   * @param scans scans to save
   * @return list of MantaMatcherScan instances
   */
  public static void saveMantaMatcherScans(String context, MediaAsset ma, Encounter enc, Set<MantaMatcherScan> scans) throws IOException {
    System.out.println("...saveScan1");
    Objects.requireNonNull(ma);
    System.out.println("...saveScan2");
    Objects.requireNonNull(scans);
    System.out.println("...saveScan3");
    // Verify that all scans belong to this SPV.
    for (MantaMatcherScan scan : scans) {
      System.out.println("scan.getDataCollectionEventId(): "+scan.getDataCollectionEventId());
      System.out.println("ma.getId(): "+ma.getId());
      if (!scan.getDataCollectionEventId().trim().equals((new Integer(ma.getId())).toString()))
        throw new IllegalArgumentException("Not all scans specified are for this SinglePhotoVideo");
    }
    System.out.println("...saveScan4");
    // Save scan data to file (or delete file if none to save).
    Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(ma);
    System.out.println("...saveScan5");
    File f = mmFiles.get("MMA-SCAN-DATA");
    System.out.println("...saveScan6");
    if (scans.isEmpty()) {
      System.out.println("...saveScan7");
      if (f.exists())
        f.delete();
      System.out.println("...saveScan8");
      return;
    }
    System.out.println("...saveScan9");
    // Save scans to file.
    try (ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(f)))) {
      System.out.println("...saveScan10");
      out.writeObject(scans);
      System.out.println("...saveScan11");
    }
    catch (Exception ex) {
      ex.printStackTrace();
      log.warn("Failed to load MantaMatcher scans for MediaAsset: " + ma.getId(), ex);
      if (ex instanceof IOException)
        throw (IOException)ex;
      throw new IOException(ex);
    }
  }

  /**
   * Constructs the URL link for displaying MantaMatcher algorithm results for the specified ID.
   * @param req servlet request
   * @param spv SinglePhotoVideo for link
   * @param scanId MantaMatcherScan ID for link
   * @return string representing URL link
   */
  public static String createMantaMatcherResultsLink(HttpServletRequest req, MediaAsset ma, Encounter enc, String scanId) {
    String paramSPV = String.format("%s=%s", MantaMatcher.PARAM_KEY_SPV, URLEncoder.encode(new Integer(ma.getId()).toString()));
    String paramScanId = String.format("%s=%s", MantaMatcher.PARAM_KEY_SCANID, URLEncoder.encode(scanId));
    String paramEncId = String.format("%s=%s", "encounterNumber", URLEncoder.encode(enc.getCatalogNumber()));
    return String.format("%s/MantaMatcher/displayResults?%s&%s&%s", req.getContextPath(), paramSPV, paramScanId, paramEncId);
  }
}
