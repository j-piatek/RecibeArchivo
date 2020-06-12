package src.es.upm.dit.tfg;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(
    name = "HelloAppEngine",
    urlPatterns = {"/hello"}
)
public class HelloAppEngine extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) 
      throws IOException {

    response.setContentType("text/plain");
    response.setCharacterEncoding("UTF-8");

    response.getWriter().print("El despliegue de este servidor fue una de las tareas realizadas para el TFG de Jakub Piatek,\r\n "
    		+ "Título del TFG: Desarrollo de una prueba de concepto para auditoría de privacidad de aplicaciones Android mediante cliente de red privada virtual");

  }
}