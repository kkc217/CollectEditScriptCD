package main;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.sql.ResultSet;

import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import db.DBManager;
import file.FileIOManager;
import jcodelib.diffutil.TreeDiff;
import jcodelib.element.GTAction;
import jcodelib.jgit.ReposHandler;

public class CollectChanges {
	//Hard-coded projects - need to read it from DB.
	public static String[] projects;
	public static String baseDir = "/Volumes/Data/scdbenchmark/subjects";
	
	public static void main(String[] args)
	{
		//Select SCD Tool here.
		String tool = "CD";
		DBManager db = null;
		try
		{
			//Change db.properties.
			db = new DBManager("db.properties");
			//Connect DB.
			Connection con = db.getConnection();
			
			//Collect project name.
			PreparedStatement subSel = con.prepareStatement("select name from subjects");
			ResultSet subSelrs = subSel.executeQuery();
            String tmp = "";
            subSelrs.next();
            tmp += subSelrs.getString("name");
            while (subSelrs.next())
			{
                tmp += "," + subSelrs.getString("name");
            }

            projects = tmp.split(",");

            subSelrs.close();
            subSel.close();
            
			//Collect and store fileId, tool, change_type, entity_type, start_pos, script, run_time in DB.
			PreparedStatement psIns = con.prepareStatement("insert into changes ( fileId, tool, change_type, entity_type, start_pos, script, run_time ) values ( ?, ?, ?, ?, ?, ?, ? )");
			for (int x=0; x < projects.length; x++)
			{
				String project = projects[x];

				System.out.println("Collecting Changes from " + project);
				String oldReposPath = baseDir + File.separator + "old" + File.separator + project + File.separator;
				String newReposPath = baseDir + File.separator + "new" + File.separator + project + File.separator;
				File oldReposDir = new File(oldReposPath);
				File newReposDir = new File(newReposPath);
				
				//Prepare files.
				List<String> fileInfo = new ArrayList<String>();
				PreparedStatement fileSel = con.prepareStatement("select a.commit_id commit_id, a.old_commit old_commit, a.new_commit new_commit, b.file file, b.file_id file_id from commits a, files b where a.commit_id = b.commit_id and a.project_name = '" + project + "'");
				ResultSet fileRS = commitSel.executeQuery();
				while (fileRS.next())
					fileInfo.add(fileRS.getString("commit_id") + "," + fileRS.getString("old_commit") + "," + fileRS.getString("new_commit") + "," + fileRS.getString("file") + "," + fileRS.getString("file_id"));
				
				fileSel.close();
				fileRS.close();
								
				System.out.println("Total " + fileInfo.size() + " revisions.");

				for (int i = 0; i < fileInfo.size(); i++)
				{
					String key = fileInfo.get(i);
					String[] tokens = key.split(",");
					String issueId = tokens[0];
					String oldCommitId = tokens[1];
					String newCommitId = tokens[2];
					String fileDir = tokens[3];
					String fileId = tokens[4];
					System.out.println("Commit " + i + " - " + oldCommitId + " " + newCommitId + "   " + issueId);
					
					//Reset hard to old/new commit IDs.
					ReposHandler.update(oldReposDir, oldCommitId);
					ReposHandler.update(newReposDir, newCommitId);

					try
					{
						if(fileDir.indexOf("/org/") < 0)
							continue;
						File oldFile = new File(oldReposPath + fileDir);
						File newFile = new File(newReposPath + fileDir);
						String oldCode = FileIOManager.getContent(oldFile).intern();
						String newCode = FileIOManager.getContent(newFile).intern();
						if(oldCode.length() == 0 || newCode.length() == 0)
						{
							//Practically these files are deleted/inserted.
							System.out.println("File " + fileId + "s are practically deleted or inserted.");
							continue;
						}

						//Apply Source Code Differencing Tools.
						//GUMTREE
//							List<GTAction> gumtreeChanges = TreeDiff.diffGumTreeWithGrouping(oldFile, newFile);
//							for(GTAction c : gumtreeChanges)
//							{
//								psIns.clearParameters();
//								psIns.setString(1, fileId); //fileId
//								psIns.setString(2, tool); //tool
//								psIns.setString(3, c.actionType); //change_type
//								psIns.setString(4, c.codeType); //entity_type
//								psIns.setInt(5, c.startPos); //start_pos
//								psIns.setString(6, c.toString()); //script
//								psIns.setInt(7, c.runTime); //run_time
//								psIns.addBatch();
//							}
					
						//CHANGE DISTILLER
						List<SourceCodeChange> changes = TreeDiff.diffChangeDistiller(oldFile, newFile);
						int runTime = TreeDiff.runTime;
						for(SourceCodeChange c : changes)
						{
							psIns.clearParameters();
							psIns.setString(1, fileId); //fileId
							psIns.setString(2, tool); //tool
							psIns.setString(3, c.getChangeType().toString()); //change_type
							psIns.setString(4, c.getChangedEntity().toString()); //entity_type
							psIns.setInt(5, 0); //start_pos
							psIns.setString(6, c.toString()); //script
							psIns.setInt(7, runTime); //run_time
							psIns.addBatch();
						}
					} catch (Exception e)
					{
						System.out.println("Error while processing "+fileId);
						e.printStackTrace();
							
					}
					
					psIns.executeBatch();
					psIns.clearBatch();
				}
			}

		} catch (Exception e)
		{
			e.printStackTrace();
		} finally
		{
			db.close();
		}
	}
}
