package src.es.upm.dit.tfg;

import java.io.File;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Paths;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsOutputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;


@WebServlet(
    name = "Recibir",
    urlPatterns = {"/RecibirArchivoServlet"}
)
@MultipartConfig(fileSizeThreshold=1024*1024*2,// 2MB
				 maxFileSize = 1024*1024*15,    // 15 MB, los archivos de Antmonitor ocupan como maximo 10MB
				 maxRequestSize = 1024*1024*50) // 50 MB
public class RecibirArchivoServlet extends HttpServlet {

	  private static final long serialVersionUID = 1L;
	  
	  private static final String BUCKET_NAME = "recibir-archivo-bucket"; // nombre del bucket iniciado en Google Cluod Storage
	  
	  private static final int BUFFER_SIZE = 1024 * 1024 * 15; // 15 MB

	  //se establece un servicio para conectar la servlet con GCS
	  private final GcsService gcsService = GcsServiceFactory.createGcsService();
	  
	  @Override
	  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
	  	
	  	System.out.println("Se ha ejecutado el metodo doPost de la servlet RecibirArchivoServlet");
	  	
	  	//Recoge parametro multi-part
	  	Part filePart = req.getPart("archivo");
	  	
	  	String name = Paths.get(filePart.getSubmittedFileName()).getFileName().toString();//se queda con el nombre original del archivo
	  	
	  	//instancia del contendor de almacenamiento 
	  	GcsFileOptions instance = GcsFileOptions.getDefaultInstance();
	  	
	  	GcsFilename fileName = new GcsFilename(BUCKET_NAME,name);
	  	GcsOutputChannel outputChannel;
	  	outputChannel = gcsService.createOrReplace(fileName, instance);
	  	
	  	//se pasa el archivo al método uploadFile que se encarga de subirlo al almacenamiento en la nube
	  	uploadFile(filePart.getInputStream(), Channels.newOutputStream(outputChannel));
	  	
	  	System.out.println("El archivo se ha subido");
	  	
	  	req.setAttribute("message","El archivo se ha subido correctamente");
	  	
	  }
	  
	  @Override
	  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		    System.out.println("Se ha ejecutado el metodo doGet de la servlet RecibirArchivoServlet");
		  	resp.setContentType("text/plain");
		    resp.setCharacterEncoding("UTF-8");
		    resp.getWriter().print("Esta es la servlet que recoge el archivo enviado al servidor y lo guarda en un bucket de Google Cloud Service,\r\n "
		    		+ "Para ello debes acceder a la servlet a traves de una petición POST");
	  	
	  }
	  
	 
	  //transfiere los datos del InputStream al OutputStream y cierra ambos streams
	  private void uploadFile(InputStream input, OutputStream output) throws IOException {
		  System.out.println("Se esta ejecutando el metodo uploadFile");
		  try {
			  byte[] buffer = new byte [BUFFER_SIZE];
			  int bytesRead = input.read(buffer);
			  while(bytesRead != -1) {
				  output.write(buffer,0, bytesRead);
				  bytesRead = input.read(buffer);
			  }
		  } finally {
			  input.close();
			  output.close();
		  }
		  
	  }
	  
	  
	  
}