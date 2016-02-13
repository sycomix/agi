package io.agi.ef.clientapi.api;

import com.sun.jersey.api.client.GenericType;

import io.agi.ef.clientapi.ApiException;
import io.agi.ef.clientapi.ApiClient;
import io.agi.ef.clientapi.Configuration;
import io.agi.ef.clientapi.Pair;


import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-02-01T23:41:55.036+11:00")
public class ConnectApi {
  private ApiClient apiClient;

  public ConnectApi() {
    this(Configuration.getDefaultApiClient());
  }

  public ConnectApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  
  /**
   * Connect to server
   * Send request to connect to server at specified address.
   * @param host ip address of machine running the server
   * @param port port of server
   * @param contextPath the context path of the server
   * @return void
   */
  public void connectHostHostPortPortContextPathContextPathGet(String host, String port, String contextPath) throws ApiException {
    Object postBody = null;
    
     // verify the required parameter 'host' is set
     if (host == null) {
        throw new ApiException(400, "Missing the required parameter 'host' when calling connectHostHostPortPortContextPathContextPathGet");
     }
     
     // verify the required parameter 'port' is set
     if (port == null) {
        throw new ApiException(400, "Missing the required parameter 'port' when calling connectHostHostPortPortContextPathContextPathGet");
     }
     
     // verify the required parameter 'contextPath' is set
     if (contextPath == null) {
        throw new ApiException(400, "Missing the required parameter 'contextPath' when calling connectHostHostPortPortContextPathContextPathGet");
     }
     
    // create path and map variables
    String path = "/connect/host/{host}/port/{port}/contextPath/{contextPath}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "host" + "\\}", apiClient.escapeString(host.toString()))
      .replaceAll("\\{" + "port" + "\\}", apiClient.escapeString(port.toString()))
      .replaceAll("\\{" + "contextPath" + "\\}", apiClient.escapeString(contextPath.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      "application/json"
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] {  };

    
    apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
}
