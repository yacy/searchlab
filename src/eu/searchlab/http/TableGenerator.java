package eu.searchlab.http;

public class TableGenerator {

    public static String example =
            "<html>\n" +
            "<head>\n" +
            "<link href=\"/css/bootstrap-custom.css\" rel=\"stylesheet\">\n" +
            "<link href=\"/css/base.css\" rel=\"stylesheet\">\n" +
            "<link href=\"/css/cinder.css\" rel=\"stylesheet\">\n" +
            "<link href=\"/css/bootstrap-table.min.css\" rel=\"stylesheet\">\n" +
            "<script src=\"/js/jquery.min.js\"></script>\n" +
            "<script src=\"/js/fontawesome-all.js\"></script>\n" +
            "<script src=\"/js/bootstrap.min.js\"></script>\n" +
            "<script src=\"/js/bootstrap-table.min.js\"></script>\n" +
            "</head><body>\n" +
            "<table id=\"table\">\n" +
            "  <thead>\n" +
            "    <tr>\n" +
            "      <th data-field=\"id\">ID</th>\n" +
            "      <th data-field=\"name\">Item Name</th>\n" +
            "      <th data-field=\"price\">Item Price</th>\n" +
            "    </tr>\n" +
            "  </thead>\n" +
            "</table>\n" +
            "\n" +
            "<script>\n" +
            "  var $table = $('#table')\n" +
            "\n" +
            "  $(function() {\n" +
            "    var data = [\n" +
            "      {\n" +
            "        'id': 0,\n" +
            "        'name': 'Item 0',\n" +
            "        'price': '$0'\n" +
            "      },\n" +
            "      {\n" +
            "        'id': 1,\n" +
            "        'name': 'Item 1',\n" +
            "        'price': '$1'\n" +
            "      },\n" +
            "      {\n" +
            "        'id': 2,\n" +
            "        'name': 'Item 2',\n" +
            "        'price': '$2'\n" +
            "      },\n" +
            "      {\n" +
            "        'id': 3,\n" +
            "        'name': 'Item 3',\n" +
            "        'price': '$3'\n" +
            "      },\n" +
            "      {\n" +
            "        'id': 4,\n" +
            "        'name': 'Item 4',\n" +
            "        'price': '$4'\n" +
            "      },\n" +
            "      {\n" +
            "        'id': 5,\n" +
            "        'name': 'Item 5',\n" +
            "        'price': '$5'\n" +
            "      }\n" +
            "    ]\n" +
            "    $table.bootstrapTable({data: data})\n" +
            "  })\n" +
            "</script>\n" +
            "</body></html>";

}