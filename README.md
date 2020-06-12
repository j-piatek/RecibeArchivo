# Repositorio con el código que completa las funcionalidades de AntMonitor

El proyecto recibir-archivo corresponde a la implementación del servidor encargado de recoger la petición HTTP POST que genera 
la aplicación y lo almacena en un bucket de Google Cloud Storage. El servidor está implementado haciendo uso de la tecnología servlet.

El archivo FileUploadService corresponde a la implementación completa del servicio Android que permite enviar un archivo al servidor 
aalojado en la nube (Google App Engine). El envío de servicio se realiza siguiendo el método FIFO. Se abre una nueva conexión HTTPS 
con el servidor para cada uno de los archivos que se quiera enviar.

Utilizando estas dos implementaciones es posible hacer uso de todas las funcionalidades de AntMonitor. El repositorio del proyecto de
AntMonitor se encuentra disponible en https://github.com/UCI-Networking-Group/AntMonitor
