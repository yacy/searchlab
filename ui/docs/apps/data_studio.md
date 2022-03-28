# Data Studio Apps

If you ever asked "How can I integrate a search function in my web page" then
this page is for you.

This is a collection of Search Widgets and Dashboards that can be used
with the Searchlab at https://searchlab.eu and in your own web pages.

## App Collection
Click on the tile to start the app:

{{#each .}}
  <a href="{{this.path}}/"><img src="{{this.path}}/screenshot.png" width="256" height="256"></a>
{{/each}}


## App Development

If you want to develop your own search apps, you can easily modify/extend
any of the given app. We recommend to start with the `websearch_lit` app,
just make a copy of it!

To start hacking, just clone this repository and open any of the `index.html`
files within the app project inside the `htdocs` folder.

Each of those apps should work fine without hosting the html pages with
a web server. Just open the corresponding `index.html` in your browser for
testing.

## Contributing Your Own Apps
If you like please give us a pull request with your new app!
We love to extend the searchlab apps with community-created content.

To do so, please..
- Create a new subfolder within `htdocs` with the name of your app
- Create a app.json and fill it with an app description using at least
  the same fields as used in `htdocs/websearch_list/app.json`.
  The app.json is used within https://searchlab.eu to show a proper visualization
  of your app.
- You must create a `index.html` file within your app folder.
- You must create a `screenshot.png` file with the exact size of 1024x1024.
  The image should not contain any transparency and it should show a mostly
  proper screenshot of your app when it is producing something useful for the user.
- You can use all `css` and `js` code as given in `htdocs/css` and `htdocs/js`,
  but you *MUST NOT* add any files to those directories. If you need any other
  `css` and `js` code, please link them directly from the internet or add those
  to your app folder in a separate `css`/`js`-path within your app folder.
- Your App must be published under the CC0 license.
- Make a pull request where only files within your app folder is added/modified,
  not anything else.

Everything that is merged to this repository will be pushed to https://searchlab.eu
and can then be used there.

## Installation
There are several options to install these apps:
- For Searchlab development and/or packaging, clone this repository aside the
  repository of searchlab. The build process will expect that the searchlab_apps
  path is in parallel.
- For integration of single apps inside your own web pages, just copy the
  corresponding app inside your own content. Because of the CC0 license you don't
  need to mention the source, just go ahead and use what you can find here.
  You will probably need to integrate `css` and `js` code from the `htdocs/css`
  and `htdocs/js` as well.
- To use your own search server, run a searchlab instance and modify the path
  to your instance in `htdocs/js/config.js`.
