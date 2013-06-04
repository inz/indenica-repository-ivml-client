
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;

import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.ac.tuwien.infosys.ws.DynamicWSClient;

import com.google.common.io.Files;

import de.uni_hildesheim.sse.ModelUtility;
import de.uni_hildesheim.sse.StandaloneInitializer;
import de.uni_hildesheim.sse.model.confModel.Configuration;
import de.uni_hildesheim.sse.model.varModel.ProgressObserver;
import de.uni_hildesheim.sse.model.varModel.Project;
import de.uni_hildesheim.sse.model.varModel.ProjectInfo;
import de.uni_hildesheim.sse.model.varModel.StringFormatException;
import de.uni_hildesheim.sse.model.varModel.VarModel;
import de.uni_hildesheim.sse.model.varModel.VarModelException;
import de.uni_hildesheim.sse.persistency.StringProvider;
import de.uni_hildesheim.sse.reasoning.core.reasoner.IReasoner;
import de.uni_hildesheim.sse.reasoning.core.reasoner.ReasoningResult;
import de.uni_hildesheim.sse.reasoning.drools.DroolsReasoner;
import eu.indenica.runtime.api.IIvmlApi;

/**
 * Small running example of an IVML stand alone application.
 * 
 * @author El-Sharkawy
 * @author Christian Inzinger
 * 
 */
public class Client extends JFrame {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);

    /**
     * To avoid annoying warning. ;-)
     */
    private static final long serialVersionUID = 5669828980662272453L;

    static {
        konfiguriereParser();
    }

    private Client() throws Exception {
        super("IVML Loader");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JLabel lbl = new JLabel("Kein Projekt geladen");


        String fileName = System.getProperty("ivml.file", "PL_SimElevator");

        LOG.info("Using {}", fileName);
            Project project = loadProject(fileName);
            if(null != project) {
                Configuration cfg = new Configuration(project);
                IReasoner drools = new DroolsReasoner();
                ReasoningResult result =
                        drools.check(project, cfg, null,
                                ProgressObserver.NO_OBSERVER);
                String conflictMsg =
                        result.hasConflict() ? " (contains errors)"
                                : " (conflict free)";
                lbl.setText("Projekt geladen: " + project.getName()
                        + conflictMsg);
                DefaultListModel<String> model = new DefaultListModel<String>();
                JList<String> liste = new JList<String>(model);
                for(int i = 0; i < project.getElementCount(); i++) {
                    model.addElement(StringProvider.toIvmlString(project
                            .getElement(i)));
                }
                getContentPane().add(
                        new JScrollPane(liste,
                                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED));
                String titel = "IVML Loader: ";
                titel +=
                        project.getVersion() != null ? project.getName()
                                + " - " + project.getVersion().getVersion()
                                : project.getName();
                setTitle(titel + conflictMsg);
            } else {
                getContentPane().add(lbl);
                setTitle("IVML Loader: No project loaded");
            }
        pack();
        setVisible(true);
    }

    private File showDialog() {
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter filter =
                new FileNameExtensionFilter("IVML Files (*.eu.indenica.runtime)", "eu.indenica.runtime");
        chooser.setFileFilter(filter);
        int returnVal = chooser.showOpenDialog(this);
        File ivmlFile = null;
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            ivmlFile = chooser.getSelectedFile();
        }

        return ivmlFile;
    }

    private static Project loadProject(String modelName) throws IOException {
        Project ivmlProject = null;
        ProjectInfo info = null;
        
        String apiUrl = "http://0.0.0.0:45690/ivml-api";
        URL apiWsdl = new URL(apiUrl + "?wsdl");

        IIvmlApi client = DynamicWSClient.createClientJaxws(IIvmlApi.class, apiWsdl);
        
        String model = client.getModel(modelName);
        File tempDir = Files.createTempDir();
        File ivmlFile = new File(tempDir, modelName + "_0.ivml");
        Writer writer = new FileWriter(ivmlFile);
        writer.write(model);
        writer.close();
        
        try {
            File directory = ivmlFile.getParentFile();
            String name = ivmlFile.getName();
            String version = ivmlFile.getName();
            int posUnderline = name.lastIndexOf('_');
            int posDot = version.lastIndexOf('.');
            name = name.substring(0, posUnderline);
            version = version.substring(posUnderline + 1, posDot);
            LOG.info("Projektname: {}", name);
            VarModel.INSTANCE.addLocation(directory,
                    ProgressObserver.NO_OBSERVER);
            info =
                    VarModel.INSTANCE.getProjectInfo(name, version,
                            ivmlFile.toURI());
        } catch(StringFormatException e) {
            LOG.error("Something went wrong", e);
        } catch(VarModelException e) {
            LOG.error("Something went wront", e);
        }
        if(null != info) {
            try {
                ivmlProject = VarModel.INSTANCE.load(info);
            } catch(VarModelException e) {
                LOG.error("Something went wrong", e);
            }
        }
        return ivmlProject;
    }

    private static void konfiguriereParser() {
        try {
            VarModel.INSTANCE.registerLoader(ModelUtility.INSTANCE,
                    ProgressObserver.NO_OBSERVER);
            ModelUtility.setResourceInitializer(new StandaloneInitializer());
        } catch(VarModelException e) {
            LOG.error("Something went wrong", e);
        }
    }

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        new Client();
    }
}
