# WordHugoPress
Small utility for converting [Wordpress](https://wordpress.org/) blog from database into [Hugo](https://gohugo.io/) site.
See [the story behind](https://nantipov.org/2019/12/converting-site-from-wordpress-into-hugo/).

# Configuration

For now, the configuration is a part of regular Spring `application.yaml` (_src/src/main/resources/application.yaml_) file. Later there will be an opportunity to externalize all relevant settings (#7).

```yaml
app:
  sources:
    source-1:
      wordpress-home: /optional/path/to/public_html1
      wordpress-remote-base-url: http://optional.website1.org/
      wordpress-table-prefix: some-table-prefix
      database:
        url: jdbc:mysql://localhost:3306/database1
        username: root
        password: mysql
      tags:
        - 'regular'
      categories:
        - 'Basic posts'
    source-2:
      wordpress-home: /optional/path/to/public_html2
      wordpress-remote-base-url: http://optional.website2.org/
      target-resource-suffix: "-s"
      database:
        url: jdbc:mysql://localhost:3306/database2
        username: root
        password: mysql
      tags:
        - 'extra'
      categories:
        - 'Limited edition posts'
  target:
    hugo-site-content-items-dir: output/blog/content/posts
```

### Sources

Here multiple sources could be declared. Local or remote or both locations could be specified, tool automatically tries to find a resource. So if you do not have a local copy, but there a workable website, just put it.

Also setting `wordpress-remote-base-url` helps for detecting inner-site or external links. Because inner-site links should be transformed into the new "folders" structure.

### Tags, categories and other taxomonies

Special `tags` or `categories` could be assigned for specific sources. And, of course, original tokens will also be migrated.

### Wordpress Table Prefix

Some of user maybe use table `prefix` for wordpress instalation, you need to supply `prefix` in configuration based on your wordpress table. The default value for prefix is `wp_`.
Reference: https://codex.wordpress.org/Creating_Tables_with_Plugins#Database_Table_Prefix

### Typical content page layout

Check file `resources/templates/empty-post.ftl` (`Freemarker` template) to get an idea about configuring the target post composition.

All fields from original `Wordpress` posts are propagated into `post` object. With some additions:
* `postDirectoryName` - path to the target `hugo` directory for the specific post;
* `thumbnailFilename` - path to the thumbnail file;
* `taxonomy` - taxonomy data as map <taxonomy name> -> <list> (e.g. `post_tag` -> `['hey', 'happy']`).

### Output 'hugo' structure

Just create an empty `hugo` site.

```sh
hugo new site blog
```

And put its path to the tool configuration.

For example.
```yaml
target:
  hugo-site-content-items-dir: output/blog/content/posts
```

# Build and run
```sh
./gradlew clean build run
```
