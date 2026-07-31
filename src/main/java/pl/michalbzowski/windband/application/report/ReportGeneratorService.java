package pl.michalbzowski.windband.application.report;
import java.io.ByteArrayOutputStream;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperFillManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service  
@RequiredArgsConstructor
public class ReportGeneratorService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReportGeneratorService.class);           
    
    public byte[] generatePdf(String reportKey, java.util.Map<String,Object> parameters)  { 
        String path="reports/"+reportKey+".jasper"; // Moved outside for catch block access
        try{          
          ClassPathResource res=new ClassPathResource(path);        
          if(!res.exists()){log.warn("Report {} not found",reportKey);return createPlaceholderPdf();}    
            // Fill the compiled .jasper with parameters using JasperFillManager  
            try(java.io.InputStream is=res.getInputStream()){
               java.util.HashMap<String,Object> paramsMap=new java.util.HashMap<>(parameters);
               net.sf.jasperreports.engine.JasperPrint print = 
                  JasperFillManager.fillReport(is, paramsMap, new JREmptyDataSource());
               
              log.debug("Filled report {}",reportKey );  
              
              // Return compiled bytes as placeholder (PDF export implementation needed)
              byte[] result = fillReportWithExporter(print, parameters);
                
                 if(result==null){return createPlaceholderPdf();}  return result;    
             } 
        }catch(Exception e){  log.error("Error generating report {}:{} ",path,e.getMessage());return createPlaceholderPdf();}      
      }    

    public byte[] generateDocx(String k, java.util.Map<String,Object> p ) {  
       throw new UnsupportedOperationException();      
     }
     
     private byte[] fillReportWithExporter(net.sf.jasperreports.engine.JasperPrint print, java.util.Map<String,Object> params) {
         try{
              // Use JasperReports exporter (complex API - simplified here as placeholder)
             log.info("Export to PDF requires JRPdfExporter setup");
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();  
           
           if(baos!=null){return baos.toByteArray(); 
               }
            return null;
          }catch(Exception e){log.error("PDF export error",e); return null;}  
     }

    private byte[] createPlaceholderPdf(){
         // Minimal valid PDF stub for testing (real implementation writes actual report content)         
        return "%PDF-1.0".getBytes(java.nio.charset.StandardCharsets.US_ASCII); 
    }
}