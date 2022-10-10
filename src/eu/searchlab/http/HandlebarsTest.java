/**
 *  HandlebarsTest
 *  Copyright 05.10.2021 by Michael Peter Christen, @orbiterlab
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.searchlab.http;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;

public class HandlebarsTest {

    final static String html = "<div class=\"container\" id=\"content\">\n"
            + "        <template id=\"stemplate\">\n"
            + "           <table>\n"
            + "              <tr>\n"
            + "                <th>Date</th>\n"
            + "                <th>Open</th>\n"
            + "              </tr>\n"
            + "              <tr class=\"tData\">\n"
            + "                {{#each values}}\n"
            + "                  <td>{{date}}</td>\n"
            + "                  <td>{{open}}</td>\n"
            + "                {{/each}}\n"
            + "              </tr>   \n"
            + "            </table>\n"
            + "          </template>\n"
            + "      </div>\n"
            + "    </div>";

    public static void main(final String[] args) {
        try {

            final Handlebars handlebars = new Handlebars();

            final JSONObject json = new JSONObject();
            final JSONArray values = new JSONArray();
            values.put(new JSONObject().put("date", "1.1.2020").put("open", 7));
            json.put("values", values);
            System.out.println(json);

            final Context context = Context
                    .newBuilder(json)
                    .resolver(JSONObjectValueResolver.INSTANCE)
                    .build();

            final Template template = handlebars.compileInline(html);
            final String htmlh = template.apply(context);
            System.out.println(htmlh);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
