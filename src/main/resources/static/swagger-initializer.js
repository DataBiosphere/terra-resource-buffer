const cleanupPlugin = function(system) {
  return {
    components: {
      // we don't need to load different specs here...
      Topbar: () => null,
      // since everything's application/json, the content-type dropdown just takes up space
      contentType: ({ value, contentTypes }) => {
        return system.React.createElement('div', null, value || contentTypes.first())
      }
    }
  }
}

window.onload = function() {
  // Begin Swagger UI call region
  const ui = SwaggerUIBundle({
    url: 'service_openapi.yaml',
    dom_id: '#swagger-ui',
    deepLinking: true,
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset
    ],
    plugins: [
      SwaggerUIBundle.plugins.DownloadUrl,
      cleanupPlugin
    ],
    layout: 'StandaloneLayout',
    defaultModelsExpandDepth: -1, // hide the huge list of schemas under the routes
    defaultModelRendering: 'model', // schema has the descriptions, unlike the example value
    defaultModelExpandDepth: 2, // affects the schema shown for a request or response
  })
  // End Swagger UI call region

  window.ui = ui
}
