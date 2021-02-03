package DTC.tools;

import java.awt.Color;
import java.util.ArrayList;

import DTC.tools.dataHandler.PointSerie;
import DTC.tools.individualData.Point;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;

/**
 * This class handles tracking of individual point-like structures
 * @author fab
 *
 */
public class tracker {
	/** Maximum allowed travel distance between two frames, in pixels **/
	static float maxDistance=10;
	
	/** Minimum number of tracked frames to consider as a proper track **/
	static int minTrackedFrames=3;
	
	/** If set, will be used to tag the outputs with a channel number **/ 
	static int channel=0;
	
	/** If set, will be used to tag the outputs with color **/ 
	static Color color=null;
	
	/**
	 * Resets all parameters to default (radius:2, tolerance: 16, enlarge: 0, tuning: false).
	 */
	public static void resetParameters() {
		maxDistance=5;
		minTrackedFrames=3;
		
		channel=0;
		color=null;
	}
	
	/**
	 * Sets all parameters:
	 * @param maxJump maximum allowed travel distance between two frames, in pixels (default: 5).
	 * @param minFrames minimum number of tracked frames to consider as a proper track (default: 3).
	 * @param C If set, will be used to tag the outputs with a channel number
	 * @param col If set, will be used to tag the outputs with color
	 */
	public static void setParameters(float maxJump, int minFrames, int C, Color col) {
		maxDistance=maxJump;
		minTrackedFrames=minFrames;
		
		channel=C;
		color=col;
	}
	
	/**
	 * Sets the maximum allowed travel distance between two frames, in pixels (default: 5).
	 * @param maxJump maximum allowed travel distance between two frames, in pixels.
	 */
	public static void setMaxDistance(float maxJump) {
		maxDistance=maxJump;
	}
	
	/**
	 * Sets the minimum number of tracked frames to consider as a proper track (default: 3).
	 * @param minFrames minimum number of tracked frames to consider as a proper track.
	 */
	public static void setMinTrackedFrames(int minFrames) {
		minTrackedFrames=minFrames;
	}
	
	/**
	 * Sets channel number for output (default: 0).
	 * @param C the channel number for output.
	 */
	public static void setChannel(int C) {
		channel=C;
	}
	
	/**
	 * Sets the color for output (default: null).
	 * @param col color for output.
	 */
	public static void setColor(Color col) {
		color=col;
	}
	
	/**
	 * Performs nearest neighbor-based tracking on the input array of PointSerie (one time point per cell).
	 * @param detections detections to link between frames, as an array of PointSerie (one time point per cell).
	 * @return an array list of PointSerie, each element being a track.
	 */
	public static ArrayList<PointSerie> doNearestNeighbor(PointSerie[] detections) {
		//Required to keep the content of "detections" untouched: direct cloning doesn't work...
		PointSerie[] toAnalyze=new PointSerie[detections.length];
		for(int i=0; i<detections.length; i++) toAnalyze[i]=detections[i].clone();
		
		
		ArrayList<PointSerie> tracks=new ArrayList<PointSerie>();
		
		for(int i=0; i<toAnalyze.length-1; i++) {
			//Get all points from the current timepoint
			PointSerie currPool=toAnalyze[i];
			
			for(int j=0; j<currPool.getNPoints(); j++) {
				//Get the current point to test
				Point currPoint=currPool.getPoint(j);
				
				//Add it to a temporary track
				PointSerie tmpTrack=new PointSerie(currPoint);
				tmpTrack.setTag(currPoint.getTag());
				
				for(int k=i+1; k<toAnalyze.length; k++) {
					PointSerie nextPool=toAnalyze[k];
					
					//Get the closest point
					float[] data=getClosestPoint(currPoint, nextPool);
					
					//If found, add to the tmp track and removes it from the pool
					if(data!=null) {
						if(data[1]<=maxDistance) {
							//Set the current PointRoi to the one we have just found
							currPoint=nextPool.getPoint((int) data[0]);
							
							//Add it to a temporary track
							tmpTrack.add(currPoint);
							
							//Tag the track
							tmpTrack.setTag(tmpTrack.getTag()+"\t"+currPoint.getTag());
							
							//Remove the closest point from t+1 (nextPool, but on original data) and reset the counter !!!
							toAnalyze[k].remove((int) data[0]);
						}
					}else {
						break;
					}
				}
				
				//Adds the tmpTrack to tracks if long enough
				if(tmpTrack.getNPoints()>minTrackedFrames) {
					tmpTrack.setName("Track_"+(tracks.size()+1));
					tracks.add(tmpTrack);
				}
			}
		}
		return tracks;
	}
	
	/**
	 * Isolates the closest point from the input point within the input list, returns its index and distance.
	 * @param point the reference point.
	 * @param pointSerie the list of points to compare to.
	 * @return the index of the closest point and its distance .
	 */
	public static float[] getClosestPoint(Point point, PointSerie pointSerie) {
		float minDist=Float.MAX_VALUE;
		int index=-1;
		
		for(int i=0; i<pointSerie.getNPoints(); i++) {
			float currDist=point.getDistance(pointSerie.getPoint(i));
			
			if(currDist<minDist) {
				index=i;
				minDist=currDist;
			}
		}
		
		if(index==-1) return null;
		
		return new float[] {index, minDist};
	}
	
	/**
	 * Pushes all the tracks to the ROI Manager and returns them as a arrayList of PointSerie.
	 * @param ip the ImagePlus on which detection has to be performed.
	 * @param channel the channel number, to tag all ROIs.
	 * @param color the color of the output ROIs.
	 * @return a detections arrayList of PointSerie.
	 */
	public static ArrayList<PointSerie> sendTracksToRoiManager(PointSerie[] detections) {
		ArrayList<PointSerie> tracks=doNearestNeighbor(detections);
		
		RoiManager rm=RoiManager.getInstance();
		if(rm==null) {
			rm=new RoiManager();
			rm.setVisible(true);
		}
		
		for(int i=0; i<tracks.size(); i++) {
			if(tracks.get(i)!=null) {
				Roi roi=tracks.get(i).toPolyline();
				
				if(color!=null) roi.setStrokeColor(color);
				roi.setName("Tracking_"+(channel==-1?"":"Channel "+channel+" ")+"Frame "+(i+1));
				roi.setPosition(channel==-1?0:channel, 1, 0);
				
				rm.add((ImagePlus) null, roi, -1);
			}
		}
		
		return tracks;
	}
	
	/**
	 * Pushes selected tracks to the ROI Manager
	 * @param tracks the tracks to push
	 * @param tag a tag to filter the ROIs to select: either Coloc, Prox or NonProxColoc
	 */
	public static void sendTracksToRoiManager(ArrayList<ArrayList<PointSerie>> tracks, String tag) {
		RoiManager rm=RoiManager.getInstance();
		if(rm==null) {
			rm=new RoiManager();
			rm.setVisible(true);
		}
		
		for(int channel=0; channel<tracks.size(); channel++) {
			for(int i=0; i<tracks.get(channel).size(); i++) {
				if(tracks.get(channel).get(i)!=null) {
					
					String currTag=tracks.get(channel).get(i).getTag();
					
					Roi roi=tracks.get(channel).get(i).toPolyline();
					
					if(color!=null) roi.setStrokeColor(detector.COLORS[channel]);
					roi.setName("Track_"+(i+1)+(channel==-1?"":" Channel "+(channel+1)+" "));
					roi.setPosition(channel==-1?0:channel, 1, 0);
					
					if(!tag.equals("All")) {
						if((currTag.indexOf("Coloc")!=-1 || currTag.indexOf("Prox")!=-1) && tag.equals("NonProxColoc")) roi=null;

						if(currTag.indexOf("Prox")==-1 && tag.equals("Prox")) roi=null;
						
						if(currTag.indexOf("Coloc")==-1 && tag.equals("Coloc")) roi=null;
						
						if((currTag.indexOf("Prox")==-1 || currTag.indexOf("Coloc")!=-1) && tag.equals("ProxOnly")) roi=null;
						
						if((currTag.indexOf("Coloc")==-1 || currTag.indexOf("Prox")!=-1) && tag.equals("ColocOnly")) roi=null;
					}
					
					if(roi!=null) rm.add((ImagePlus) null, roi, -1);
				}
			}
		}
	}
	
	/**
	 * Pushes selected tracks to the ResultsTable
	 * @param tracks the tracks to push
	 * @param tag a tag to filter the ROIs to select: either Coloc, Prox or NonProxColoc
	 */
	public static void sendTracksToResultsTable(ArrayList<ArrayList<PointSerie>> tracks, String tag) {
		ResultsTable rt=new ResultsTable();
		
		
		for(int channel=0; channel<tracks.size(); channel++) {
			for(int i=0; i<tracks.get(channel).size(); i++) {
				if(tracks.get(channel).get(i)!=null) {
					boolean doLog=true;
					String currTag=tracks.get(channel).get(i).getTag();
					
					
					if(!tag.equals("All")) {
						if((currTag.indexOf("Coloc")!=-1 || currTag.indexOf("Prox")!=-1) && tag.equals("NonProxColoc")) doLog=false;
						
						if(currTag.indexOf("Prox")==-1 && tag.equals("Prox")) doLog=false;
						
						if(currTag.indexOf("Coloc")==-1 && tag.equals("Coloc")) doLog=false;
						
						if((currTag.indexOf("Prox")==-1 || currTag.indexOf("Coloc")!=-1) && tag.equals("ProxOnly")) doLog=false;
						
						if((currTag.indexOf("Coloc")==-1 || currTag.indexOf("Prox")!=-1) && tag.equals("ColocOnly")) doLog=false;
					}
					
					if(doLog) tracks.get(channel).get(i).toResultsTable(i+1, channel+1, rt);
				}
			}
		}
		rt.show(tag);
	}
}
