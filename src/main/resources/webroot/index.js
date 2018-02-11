/* global $, CodeMirror, alert */

$(function () {
  let source = CodeMirror.fromTextArea($('#input').get(0), {
    mode: 'text/x-java',
    lineNumbers: true,
    matchBrackets: true
  })

  let run = () => {
    let toRun = source.getValue()
    if (toRun.trim() === "") {
      $("#output pre").text(`> Hit Control-Enter to run your Java code...`)
      return
    }

    $.post("/run", JSON.stringify({
      source: source.getValue() + "\n"
    })).done(result => {
      console.log(result)
      if (result.completed) {
        $("#output pre").text(result.output)
      } else if (result.timeout) {
        $("#output pre").html(`<span class="text-danger">Timeout</span>`)
      } else if (!result.compiled) {
        $("#output pre").html(`<span class="text-danger">Compiler error:\n${ result.compileError }</span>`)
      } else if (!result.completed) {
        $("#output pre").html(`<span class="text-danger">Runtime error:\n${ result.runtimeError }</span>`)
      }
    }).fail((xhr, status, error) => {
      console.error("Request failed")
      console.error(JSON.stringify(xhr, null, 2))
      console.error(JSON.stringify(status, null, 2))
      console.error(JSON.stringify(error, null, 2))
    })
  }

  $(window).keypress(function (event) {
    if (!(event.which === 13 && event.ctrlKey) &&
      !(event.which === 10 && event.ctrlKey)) {
      return true
    }
    event.preventDefault()
    run()
  })
})
