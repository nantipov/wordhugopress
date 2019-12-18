<#function taxname tax>
    <#if tax == "category"><#return "categories"/><#elseif tax == "post_tag"><#return "tags"/><#else><#return tax/></#if>
</#function>
---
title: '${post.title}'
author: ${post.author}
date: ${post.createdAt?iso_local}
publishdate: ${post.createdAt?iso_local}
lastmod: ${post.modifiedAt?iso_local}
draft: ${post.draft?string("true", "false")}
description: '${post.title}'
<#if post.thumbnailFilename?has_content>
cover: 'posts/${post.postDirectoryName}/${post.thumbnailFilename}'
</#if>
<#list post.taxonomy.keySet() as taxomony>
${taxname(taxomony)}: [${post.taxonomy.get(taxomony)?map(t -> "'" + t + "'")?join(", ")}]
</#list>
---

${post.processedContent}
