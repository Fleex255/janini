/* global $, CodeMirror, alert */

$(function () {
  let source = CodeMirror.fromTextArea($('#input').get(0), {
    mode: 'text/x-java',
    lineNumbers: true,
    matchBrackets: true
  })

  $(window).keypress(function (event) {
    if (!(event.which === 115 && event.ctrlKey) && !(event.which === 19)) {
      return true
    }
    event.preventDefault()

    let toRun = source.getValue()
    if (toRun.trim() === "") {
      $("#output pre").text(`> Hit Control-S to run your Java code...`)
      return
    }

    $.post("run", JSON.stringify({
      source: source.getValue()
    })).done(result => {
      console.log(JSON.stringify(result, null, 2))
      if (result.completed) {
        $("#output pre").text(result.output)
      } else if (result.timeout) {
        $("#output pre").html(`<span class="text-danger">Timeout</span>`)
      } else if (!result.compiled) {
        $("#output pre").html(`<span class="text-danger">Compiler error:\n${ result.compileError }</span>`)
      } else if (!result.ran) {
        $("#output pre").html(`<span class="text-danger">Runtime error:\n${ result.runtimeError }</span>`)
      }
    }).fail((xhr, status, error) => {
      console.error("Request failed")
      console.error(JSON.stringify(xhr, null, 2))
      console.error(JSON.stringify(status, null, 2))
      console.error(JSON.stringify(error, null, 2))
    })
  })
})
